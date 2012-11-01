import sys, re, os, glob

import gat
import gat.IOTools as IOTools
import gat.Experiment as E
import numpy

try:
    import matplotlib.pyplot as plt
    HASPLOT = True
except (ImportError,RuntimeError):
    HASPLOT = False

##################################################
##################################################
##################################################
# process input
def dumpStats( coll, section, options ):
    if section in options.output_stats or \
            "all" in options.output_stats or \
            len( [ x for x in options.output_stats if re.search( x, section ) ] ) > 0:
        coll.outputStats( E.openOutputFile( section ) )

def dumpBed( coll, section, options  ):
    if section in options.output_bed or \
            "all" in options.output_bed or \
            len( [ x for x in options.output_bed if re.search( x, section ) ] ) > 0:
        coll.save( E.openOutputFile( section + ".bed" ) )

def readSegmentList( label, filenames, options, enable_split_tracks = False ):
    # read one or more segment files
    results = gat.IntervalCollection( name = label )
    E.info( "%s: reading tracks from %i files" % (label, len(filenames)))
    results.load( filenames, split_tracks = enable_split_tracks )
    E.info( "%s: read %i tracks from %i files" % (label, len(results), len(filenames)))
    dumpStats( results, "stats_%s_raw" % label, options )
    results.normalize()
    dumpStats( results, "stats_%s_normed" % label, options )
    return results

def expandGlobs( infiles ):
    return IOTools.flatten( [ glob.glob( x ) for x in infiles ] )

def buildSegments( options ):
    '''load segments, annotations and workspace from parameters
    defined in *options*.

    The workspace will be split by isochores.

    returns segments, annotations and workspace.
    '''

        
    options.segment_files = expandGlobs( options.segment_files )
    options.annotation_files = expandGlobs( options.annotation_files )
    options.workspace_files = expandGlobs( options.workspace_files )
    options.sample_files = expandGlobs( options.sample_files )

    ##################################################
    # arguments sanity check
    if not options.segment_files:
        raise ValueError("please specify at least one segment file" )
    if not options.annotation_files:
        raise ValueError("please specify at least one annotation file" )
    if not options.workspace_files:
        raise ValueError("please specify at least one workspace file" )


    # read one or more segment files
    segments = readSegmentList( "segments", options.segment_files, options)
    if options.ignore_segment_tracks:
        segments.merge( delete = True)
        E.info( "merged all segments into one track with %i segments" % len(segments))

    if len(segments) > 1000: 
        raise ValueError( "too many (%i) segment files - use track definitions or --ignore-segment-tracks" % len(segments) )
    
    annotations = readSegmentList( "annotations", options.annotation_files, options, options.enable_split_tracks )
    workspaces = readSegmentList( "workspaces", options.workspace_files, options, options.enable_split_tracks )

    # intersect workspaces to build a single workspace
    E.info( "collapsing workspaces" )
    dumpStats( workspaces, "stats_workspaces_input", options )
    workspaces.collapse()
    dumpStats( workspaces, "stats_workspaces_collapsed", options )

    # use merged workspace only, discard others
    workspaces.restrict("collapsed")

    # build isochores or intersect annotations/segments with workspace
    if options.isochore_files:
        
        # read one or more isochore files
        isochores = gat.IntervalCollection( name = "isochores" )
        E.info( "%s: reading isochores from %i files" % ("isochores", len(options.isochore_files)))
        isochores.load( options.isochore_files )
        dumpStats( isochores, "stats_isochores_raw", options )

        # merge isochores and check if consistent (fully normalized)
        isochores.sort()

        # check that there are no overlapping segments within isochores
        isochores.check()

        # TODO: flag is_normalized not properly set
        isochores.normalize()

        # check that there are no overlapping segments between isochores

        # truncate isochores to workspace
        # crucial if isochores are larger than workspace.
        isochores.intersect( workspaces["collapsed"] )

    else:
        isochores = None
    
    return segments, annotations, workspaces, isochores

def applyIsochores( segments, annotations, workspaces, options, isochores = None ):
    '''apply isochores to segments, annotations.

    Segments and annotations are filtered and truncated to keep 
    only those overlapping the workspace.

    If no isochores are given, isochores are not applied.

    returns workspace divided into isochores.
    '''

    if isochores:
        # intersect isochores and workspaces, segments and annotations
        E.info( "adding isochores to workspace" )
        workspaces.toIsochores( isochores )
        annotations.toIsochores( isochores )
        segments.toIsochores( isochores )
        
        if workspaces.sum() == 0:
            raise ValueError( "isochores and workspaces do not overlap" )
        if annotations.sum() == 0:
            raise ValueError( "isochores and annotations do not overlap" )
        if segments.sum() == 0:
            raise ValueError( "isochores and segments do not overlap" )

        dumpStats( workspaces, "stats_workspaces_isochores", options )
        dumpStats( annotations, "stats_annotations_isochores", options )
        dumpStats( segments, "stats_segments_isochores", options )
    
        dumpBed( workspaces, "workspaces_isochores", options )
        dumpBed( annotations, "annotations_isochores", options )
        dumpBed( segments, "segments_isochores", options )

    else:
        # intersect workspace and segments/annotations
        # annotations and segments are truncated by workspace
        annotations.intersect( workspaces["collapsed"] )
        segments.intersect( workspaces["collapsed"] )
        
        dumpStats( annotations, "stats_annotations_truncated", options )
        dumpStats( segments, "stats_segments_truncated", options )

    workspace = workspaces["collapsed"] 

    if options.restrict_workspace:

        E.info( "restricting workspace" )
        # this is very cumbersome - refactor merge and collapse
        # to return an IntervalDictionary instead of adding it
        # to the list of tracks
        for x in (segments, annotations):
            if "merged" in segments:
                workspace.filter( segments["merged"] )
            else:
                segments.merge()
                workspace.filter( segments["merged"] )
                del segments[merged]

        dumpStats( workspaces, "stats_workspaces_restricted", options )
        
    if options.truncate_workspace_to_annotations:

        E.info( "truncating workspace to annotations" )
        annotations.merge()
        workspace.intersect( annotations["merged"] )
        del annotations["merged"]

        dumpStats( workspaces, "stats_workspaces_truncated", options )

    # segments.dump( open("segments_dump.bed", "w" ) )
    # workspaces.dump( open("workspaces_dump.bed", "w" ) )

    # output overlap stats
    # output segment densities per workspace
    if "overlap" in options.output_stats or \
            "all" in options.output_stats:
        for track in segments.tracks:
            workspaces.outputOverlapStats( E.openOutputFile( "overlap_%s" % track), 
                                           segments[track] )

    return workspace


def readDescriptions( options ):
    '''read descriptions from tab separated file.'''

    description_header, descriptions, description_width = [], {}, 0
    if options.input_filename_descriptions:
        E.info( "reading descriptions from %s" % options.input_filename_descriptions )

        with IOTools.openFile( options.input_filename_descriptions ) as inf:
            first = True
            for line in inf:
                if line.startswith("#"): continue
                data = line[:-1].split( "\t" )

                if description_width: 
                    assert len(data) -1 == description_width, "inconsistent number of descriptions in %s" % options.input_filename_descriptions
                else: description_width = len(data) - 1

                if first: 
                    description_header = data[1:]
                    first = False
                else:
                    descriptions[data[0]] = data[1:]
        assert len(description_header) == description_width, "number of descriptions (%i) inconsistent with header (%s) in %s" % \
            ( description_width, len(description_header), options.input_filename_descriptions)

    return description_header, descriptions, description_width

def outputResults( results, 
                   options, 
                   header, 
                   description_header, 
                   description_width,
                   descriptions,
                   format_observed = "%i" ):
    '''compute FDR and output results.'''
    
    pvalues = [ x.pvalue for x in results ]

    ##################################################
    ##################################################
    ##################################################
    ## compute global fdr
    ##################################################
    E.info( "computing FDR statistics" )
    qvalues = gat.getQValues( pvalues, 
                              method = options.qvalue_method,
                              vlambda = options.qvalue_lambda,
                              pi0_method = options.qvalue_pi0_method )

    try:
        results = [ x._replace( qvalue = qvalue ) for x, qvalue in zip(results, qvalues) ]
        is_tuple = True
    except AttributeError:
        # not a namedtuple
        for x, qvalue in zip(results, qvalues): 
            x.qvalue = qvalue
            x.format_observed = format_observed

        is_tuple = False

    counters = set([x.counter for x in results ] )

    for counter in counters:

        if len(counters) == 1:
            outfile = options.stdout
            output = results
        else:
            outfilename = re.sub("%s", counter, options.output_tables_pattern )
            E.info( "output for counter %s goes to outfile %s"  % (counter, outfilename ))
            outfile = IOTools.openFile( outfilename, "w" )
            output = [ x for x in results if x.counter == counter ]

        outfile.write("\t".join( list(header) + list(description_header) ) + "\n" )

        if options.output_order == "track":
            output.sort( key = lambda x: (x.track, x.annotation) )
        elif options.output_order == "annotation":
            output.sort( key = lambda x: (x.annotation, x.track) )
        elif options.output_order == "fold":
            output.sort( key = lambda x: x.fold )
        elif options.output_order == "pvalue":
            output.sort( key = lambda x: x.pvalue )
        elif options.output_order == "qvalue":
            output.sort( key = lambda x: x.qvalue )
        else:
            raise ValueError("unknown sort order %s" % options.output_order )

        for result in output:
            if is_tuple:
                outfile.write( "\t".join( map(str, result) ) )
            else:
                outfile.write( str(result ))

            if descriptions:
                try:
                    outfile.write( "\t" + "\t".join( descriptions[result.annotation] ) )
                except KeyError:
                    outfile.write( "\t" + "\t".join( [""] * description_width ) )
            outfile.write("\n")
            
        if outfile != options.stdout:
            outfile.close()

def plotResults( results, options ):
    '''plot annotator results.'''

    ##################################################
    # plot histograms
    if options.output_plots_pattern and HASPLOT:

        def buildPlotFilename( options, key ):
            filename = re.sub("%s", key, options.output_plots_pattern)
            filename = re.sub("[^a-zA-Z0-9-_./]", "_", filename )
            dirname = os.path.dirname( filename )
            if dirname and not os.path.exists( dirname ): os.makedirs( dirname )
            return filename

        E.info("plotting sample stats" )

        for r in results:

            plt.figure()
            key = "%s-%s-%s" % (r.track, r.annotation, r.counter)
            s = r.samples
            hist, bins = numpy.histogram( s,
                                          bins = 100)
            
            # convert to density
            hist = numpy.array( hist, dtype = numpy.float )
            hist /= sum(hist)

            # plot bars
            plt.bar( bins[:-1], hist, width=1.0, label = key )
            
            # plot estimated 
            sigma = r.stddev
            mu = r.expected
            plt.plot(bins, 
                     1.0/(sigma * numpy.sqrt(2 * numpy.pi)) *
                     numpy.exp( - (bins - mu)**2 / (2 * sigma**2) ),
                     label = "std distribution",
                     linewidth=2, 
                     color='r' )

            plt.legend()
            filename = buildPlotFilename( options, key )
            plt.savefig( filename )

        E.info( "plotting P-value distribution" )
        
        key = "pvalue"
        plt.figure()

        x,bins,y = plt.hist( [r.pvalue for r in results ],
                             bins = numpy.arange( 0, 1.05, 0.025) ,
                             label = "pvalue" )

        plt.hist( [r.qvalue for r in results ],
                  bins = numpy.arange( 0, 1.05, 0.025) ,
                  label = "qvalue",
                  alpha=0.5 )

        plt.legend()

        # hist, bins = numpy.histogram( \
        #     [r.pvalue for r in gat.iterator_results(annotator_results) ],
        #     bins = 20 )
        # plt.plot( bins[:-1], hist, label = key )

        filename = buildPlotFilename( options, key )
        plt.savefig( filename )


