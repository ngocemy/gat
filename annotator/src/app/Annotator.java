package app;

/* todo:
 *
 * - Verander 'synonym' in 'isochore' ofzo
 * - Verander 'workspace' in 'prior' ofzo...?
 * - Line-based GFF-like input / output
 * - Parsed nn lines, only for verbose>0
 * - Errors in estimated averages?  Mooiere output lijst
 * - Segment overhang in andere isochores
 * - remove fdrmaxp etc?
 */

import java.io.LineNumberReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Random;
import java.util.Date;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;


class Annotator {
	
	public static void main(String[] arguments) {
		
        int iterations = 100;
        int histsize = 100000;
        int bucketsize = 1;
        double zThreshold = 0.0;        // no limit
        double pThreshold = 1.0;        // no limit
        double densityThreshold = 0.0;  // no limit
        double baseline = 1.00;
        int verbose = 1;
        long seed = 1;
        int shoulder = 0;
        double maxpvalue = 0.025;
        boolean keepOverhang = true;
        boolean dumpSamples = false;
        boolean dumpSegments = false;
	boolean intersected = false;
	Samples samples = null;
	String version = "0.9 23-nov-07";
        
        Options opt = new Options();

        opt.addOption("h", false, "Print help for this application");
        opt.addOption("workspace", true, "Genomic segments to consider (filename)");
        opt.addOption("workspace2", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
        opt.addOption("workspace3", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace4", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace5", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace6", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace7", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace8", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
	opt.addOption("workspace9", true, "Genomic segments to consider, will be intersected with other workspace (filename; optional)");
        opt.addOption("synonyms", true, "Chromosome synonyms for ids and segments (filename; optional)");
        opt.addOption("subsets", true, "Subsets of synonyms to analyze separately (filename; optional)");
        opt.addOption("annotation", true, "Annotation-to-id map (filename)");
        opt.addOption("ids", true, "Id-to-segment map (filename)");
        opt.addOption("segments", true, "Input data (filename)");
	opt.addOption("samples", true, "External null samples (filename; contains list of filenames)");
        opt.addOption("iterations", true, "Number of iterations (default "+iterations+")");
        opt.addOption("mindensity", true, "Threshold for density (either observed or expected; default "+densityThreshold+")");
        opt.addOption("fdrmaxp", true, "Maximum p-value considered for FDR summary (default "+maxpvalue+"), decrease to use less memory");
        opt.addOption("baseline", true, "Baseline for over/underrepresentation p-value, relative to randomized proportion (default 1.00)");
        opt.addOption("histsize", true, "Histogram size for empirical segment length distribution (default "+histsize+")");
        opt.addOption("bucketsize", true, "Bucket size for empirical segment length distribution (default "+bucketsize+")");
        opt.addOption("keepoverhang", true, "Keep overhang for empirical seglen distribution in workspace intersection (default "+keepOverhang+"; false may make sense if intersected=true)");
        opt.addOption("verbose", true, "Verbosity level (default "+verbose+", range 0-3.)");
        opt.addOption("seed", true, "Random seed (default "+seed+")");
        opt.addOption("dumpsamples", false, "Dump observed and all sampled proportions for every annotation category (no argument)");
        opt.addOption("dumpsegments", false, "Dump sampled segments (no argument)");
        opt.addOption("shoulder", true, "Shoulder to add (left and right) to segments (default 0)");
	opt.addOption("intersected", false, "Intersect sampled segments with isochores and get exact nuc counts per isochore, instead of allowing overhang (no args)");

        BasicParser optParser = new BasicParser();
        CommandLine cl;
        try {
        	cl = optParser.parse(opt,arguments);
        } catch ( ParseException e ) {
        	throw new Error("Parse error: "+e.getMessage()+"\nUse -h for help");
        }

        if ( cl.hasOption('h') ) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", opt);
            System.exit(0);
        }
        
        // Deal with options
        String[] options = {"workspace",
			    "workspace2","workspace3","workspace4","workspace5","workspace6","workspace7",
			    "workspace6","workspace7","workspace8","workspace9",
			    "annotation","ids","segments","synonyms","subsets"};
        AnnotationData data = new AnnotationData();
        Parser parser = new Parser( data );
        
        if (cl.hasOption("iterations")) {
        	iterations = Integer.valueOf( cl.getOptionValue("iterations") ).intValue();
        }
        if (cl.hasOption("minz")) {
        	zThreshold = Double.parseDouble( cl.getOptionValue("minz") );
        }
        if (cl.hasOption("maxp")) {
        	pThreshold = Double.parseDouble( cl.getOptionValue("maxp") );
        }
        if (cl.hasOption("mindensity")) {
        	densityThreshold = Double.parseDouble( cl.getOptionValue("mindensity") );
		if (densityThreshold > 0.0) {
		    System.out.println("# Warning: Nonzero density threshold; the FDR statistics will be inaccurate (conservative)!");
		}
        }
        if (cl.hasOption("verbose")) {
        	verbose = Integer.parseInt( cl.getOptionValue("verbose"));
        }
        if (cl.hasOption("seed")) {
        	seed = Integer.parseInt( cl.getOptionValue("seed"));
        }
        if (cl.hasOption("baseline")) {
        	baseline = Double.parseDouble( cl.getOptionValue("baseline"));
        }
        if (cl.hasOption("fdrmaxp")) {
        	maxpvalue = Double.parseDouble( cl.getOptionValue("fdrmaxp"));
        }
        if (cl.hasOption("histsize")) {
        	histsize = Integer.parseInt( cl.getOptionValue("histsize"));
        }
        if (cl.hasOption("bucketsize")) {
        	bucketsize = Integer.parseInt( cl.getOptionValue("bucketsize"));
        }
        if (cl.hasOption("keepoverhang")) {
        	keepOverhang = Boolean.valueOf( cl.getOptionValue("keepoverhang")).booleanValue();
        }
        if (cl.hasOption("shoulder")) {
        	shoulder = Integer.parseInt( cl.getOptionValue("shoulder") );
        }
        if (cl.hasOption("dumpsamples")) {
        	dumpSamples = true;
        }
        if (cl.hasOption("dumpsegments")) {
        	dumpSegments = true;
        }
	if (cl.hasOption("intersected")) {
	    intersected = true;
	}
	if (cl.hasOption("samples")) {
	    String sampleFile = cl.getOptionValue("samples");
	    samples = new Samples( sampleFile );
	}
        
        if (verbose > 0) {
	    System.out.println("# Annotator version "+version);
	    System.out.println("# Parameters:");
	    System.out.println("#  iterations "+iterations);
	    System.out.println("#  baseline "+baseline);
	    System.out.println("#  shoulder "+shoulder);
	    System.out.println("#  mindensity "+densityThreshold); 
	    System.out.println("#  fdrmaxp "+maxpvalue);
	    System.out.println("#  keepoverhang "+keepOverhang);
	    System.out.println("#  verbose "+verbose);
	    System.out.println("#  seed "+seed);
	    System.out.println("#  histsize "+histsize);
	    System.out.println("#  bucketsize "+bucketsize);
	    System.out.println("#  keepoverhang "+keepOverhang);
	    System.out.println("#  dumpsamples "+dumpSamples);
	    System.out.println("#  dumpsegments "+dumpSegments);
	    System.out.println("#  intersected "+intersected);
        }

        for (int i=0; i<options.length; i++) {
        	if (cl.hasOption(options[i])) {

        		String filename = cl.getOptionValue(options[i]);
        		LineNumberReader in;
        		
        		try {
        			in = new LineNumberReader(new FileReader( filename ));
        			if (verbose>0) {
        				System.out.println("# Reading file "+filename+" for option "+options[i]);
        			}
        		} catch (FileNotFoundException e) {	
        			throw new Error("File "+filename+" not found");
        		}
       		
        		try {
        			parser.parseAnnotationData( in, verbose, shoulder );
        		} catch (Error e) {
        			throw new Error("Error parsing file "+filename+":\n"+e.getMessage());
        		}
        	}
        }

		// Set current data set to input segments, calculate intersection with
		// workspace, and length distribution

        if (verbose > 0) {
        	parser.dumpSummary(verbose);
        }

        data.intersectWorkspaces( verbose );

        data.addDefaultSynonyms( verbose );
        
        data.checkSubsets( verbose );

        data.checkMutualWorkspaceIntersection();

        data.checkChroms();
        
		Random random = new Random(seed);

		int numIDs = data.ids.size();

		int numUndefIDs = data.undefinedIDs( verbose );
		
		int numPurged = data.purgeUnusedIDs();

		int numNotInWorkspace = data.removeIdsOutsideWorkspace();

		int numEmptyAnnotations = data.removeEmptyAnnotations( verbose );
		
		int numDuplicatedAnnotations = data.removeDuplicatedAnnotations( verbose  );

		data.initialize(random, baseline, iterations, maxpvalue, verbose, histsize, bucketsize, keepOverhang, dumpSamples, intersected);
		
		data.reportSegmentIntersection( verbose );
		
		// Perform first calculation
		
		Iterator subsetiter = data.subsets.keySet().iterator();
		while (subsetiter.hasNext()) {
		    String subset = (String)subsetiter.next();
		    data.calculateIdintersection(true, subset);
		    data.calculate(-1, dumpSamples, subset, false);
		}

		if (dumpSamples) {
		    System.out.println();
		}
		
		if (verbose > 0) {
			data.dumpSummary(numIDs, numNotInWorkspace, numPurged, numEmptyAnnotations, numDuplicatedAnnotations, numUndefIDs );
		}

		// Remove segments to save memory

		data.segments = null;

		// Run iterations
		
		for (int iters=0; iters<iterations; iters++) {
		    if (verbose > 5)
			System.out.println("start:"+new Date());
		    if (!dumpSamples && !dumpSegments) {
		    	if (verbose > 1) {
			    System.out.println("Iteration "+iters);
		    	} else if (verbose == 1 && (iters%100 == 0)) {
			    System.out.println("Iteration "+iters);
		    	}
		    }
		    
		    if (dumpSegments) {
			System.out.println("##Iteration\t"+iters);
		    }
		    
		    if (samples != null) {
			if (!samples.setSamples( data )) {
			    break;
			}
		    } else {
			if (intersected) {
			    data.randomSample(dumpSegments);
			} else {
			    data.randomSample2(dumpSegments);
			}
		    }
		    
		    subsetiter = data.subsets.keySet().iterator();
		    while (subsetiter.hasNext()) {
		    	String subset = (String)subsetiter.next();
		    	data.calculateIdintersection(false, subset);
		    	data.calculate(iters,dumpSamples, subset, (samples != null) );
		    }
		    if (dumpSamples) {
			    System.out.println();
			}

		}

		// Report
		
		int removed = data.removeLowSupportAnnotations( densityThreshold );
		if (verbose > 0) {
			System.out.println("# Removed "+removed+" annotations with low density");
		}

		data.dumpResults( zThreshold, pThreshold, iterations, dumpSamples );
		
	}
}
