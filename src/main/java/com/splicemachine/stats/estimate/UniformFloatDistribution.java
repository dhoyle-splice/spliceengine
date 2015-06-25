package com.splicemachine.stats.estimate;

import com.splicemachine.stats.FloatColumnStatistics;
import com.splicemachine.stats.frequency.FloatFrequencyEstimate;
import com.splicemachine.stats.frequency.FloatFrequentElements;
import com.splicemachine.utils.ComparableComparator;

import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 3/5/15
 */
public class UniformFloatDistribution extends BaseDistribution<Float> implements FloatDistribution {
    private final double a;
    private final double b;

    public UniformFloatDistribution(FloatColumnStatistics columnStats) {
        super(columnStats, ComparableComparator.<Float>newComparator());

        if(columnStats.nonNullCount()==0){
            /*
             * The distribution is empty, so the interpolation function is 0
             */
            this.a = 0d;
            this.b = 0d;
        }else if(columnStats.max()==columnStats.min()){
            /*
             * The distribution contains a single element, so the interpolation function is a constant
             */
            this.a = 0d;
            this.b = columnStats.minCount();
        }else{
            double at=columnStats.nonNullCount()-columnStats.minCount();
            at/=(columnStats.max()-columnStats.min());

            this.a=at;
            this.b=columnStats.nonNullCount()-a*columnStats.max();
        }
    }

    @Override
    protected long estimateRange(Float start, Float stop, boolean includeStart, boolean includeStop, boolean isMin) {
        return rangeSelectivity(start,stop,includeStart,includeStop,isMin);
    }

    @Override
    public long selectivity(float value) {
        FloatColumnStatistics fcs = (FloatColumnStatistics)columnStats;
        if(value<fcs.min()) return 0l;
        else if(value==fcs.min()) return fcs.minCount();
        else if(value>fcs.max()) return 0l;

        FloatFrequentElements ffe = (FloatFrequentElements)fcs.topK();
        FloatFrequencyEstimate floatFrequencyEstimate = ffe.countEqual(value);
        if(floatFrequencyEstimate.count()>0) return floatFrequencyEstimate.count();

        //not a frequent element, so estimate the value using cardinality and adjusted row counts
        return getAdjustedRowCount()/fcs.cardinality();
    }

    @Override
    public long selectivityBefore(float stop, boolean includeStop) {
        FloatColumnStatistics fcs = (FloatColumnStatistics)columnStats;
        float min = fcs.min();
        if(stop<min||(!includeStop && stop==min)) return 0l;

        return rangeSelectivity(min,stop,true,includeStop);
    }

    @Override
    public long selectivityAfter(float start, boolean includeStart) {
        FloatColumnStatistics fcs = (FloatColumnStatistics)columnStats;
        float max = fcs.max();
        if(start>max ||(!includeStart &&start==max)) return 0l;
        return rangeSelectivity(start,max,includeStart,true);
    }

    @Override
    public long rangeSelectivity(float start, float stop, boolean includeStart, boolean includeStop) {
        FloatColumnStatistics fcs = (FloatColumnStatistics)columnStats;
        float min = fcs.min();
        if(stop<min||(!includeStop && stop==min)) return 0l;
        else if(includeStop && stop==min) return selectivity(stop);

        float max = fcs.max();
        if(max<start||(!includeStart && start==max)) return 0l;
        else if(includeStart && start==min) return selectivity(start);

        boolean isMin = false;
        if(start<=min){
            includeStart = includeStart||start<min;
            start = min;
            isMin=true;
        }
        if(stop>max){
            stop = max;
            includeStop = true;
        }
        return rangeSelectivity(start,stop,includeStart,includeStop,isMin);
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private long rangeSelectivity(float start, float stop, boolean includeStart, boolean includeStop,boolean isMin) {
        double baseEstimate = a*stop+b;
        baseEstimate-=a*start+b;
        long perRowCount = getPerRowCount();
        if(!includeStart){
            baseEstimate-=perRowCount;
        }
        if(includeStop)
            baseEstimate+=perRowCount;

        FloatFrequentElements ife = (FloatFrequentElements)columnStats.topK();
        //if we are the min value, don't include the start key in frequent elements
        includeStart = includeStart &&!isMin;
        Set<FloatFrequencyEstimate> ffe = ife.frequentBetween(start, stop, includeStart, includeStop);
        baseEstimate-=perRowCount*ffe.size();
        for(FloatFrequencyEstimate est:ffe){
            baseEstimate+=est.count()-est.error();
        }
        return (long)baseEstimate;
    }

    private long getPerRowCount() {
        return getAdjustedRowCount()/columnStats.cardinality();
    }
}
