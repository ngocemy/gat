package app;


import segment.SegmentList;
import segment.Segment;
import app.Globals;

import java.util.Random;
import java.util.Arrays;


class SegmentListSampler {
	
	
	public SegmentListSampler( SegmentList segList, Random random ) {
		
		this.segList = segList;
		this.random = random;
		// Initialize cumulative histogram
		cdf = new long[segList.numSegments()];
		long totsize = 0;
		for (int i=0; i<cdf.length; i++) {
			totsize += segList.get(i).length();
			cdf[i] = totsize;
		}
		
	}

	
	
	public long sampleDumb() {
				
		// dumb implementation
		long max = segList.max();
		long r = Math.abs(random.nextLong()) % max;
		while ( segList.intersection( new Segment(r,r+1) ) == 0) { 
			r = Math.abs(random.nextLong()) % max;
		}
		return r;
		
	}
	
	public long sample() {
		
	    if (cdf.length == 0) {
		System.out.println("###Warning -- sampling from empty segment list.  Probably no problem.");
		return 1;
	    }
		long r = Math.abs(random.nextLong()) % cdf[cdf.length-1];
		int seg = Arrays.binarySearch( cdf, r );
		if (seg < 0) {
			seg = -(seg+1);   // insertion point
		} else {
			seg += 1;
		}
		long sample = segList.get(seg).right() + ( r - cdf[seg] );
		if (Globals.doChecks) {
			if (segList.intersection( new Segment(sample,sample+1) ) != 1) {
				throw new Error("Obtained sample outside segment list");
			}
		}
		return sample;
		
	}
	

    public Random getRandom() { return random; }


	
	SegmentList segList;	
	long cdf[];
	Random random;
	
}
