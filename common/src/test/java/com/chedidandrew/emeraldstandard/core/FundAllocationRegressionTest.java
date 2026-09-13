package com.chedidandrew.emeraldstandard.core;
public final class FundAllocationRegressionTest {
    public static void main(String[] args) {
        for(var type:EconomyState.ProsperityFundType.values())
            for(var purpose:EconomyState.DonationPurpose.values())
                for(double fraction:new double[]{0,0.1,0.2,0.9})
                    for(long amount:new long[]{0,1,100*EconomyState.MICRO,1_000_000*EconomyState.MICRO}) {
                        long r=FundAllocation.reserve(type,purpose,amount,fraction);
                        long expected=type==EconomyState.ProsperityFundType.DIRECT_GRANT&&purpose!=EconomyState.DonationPurpose.RESTORATION
                            ?Math.round(amount*fraction):0;
                        if(r!=expected||r<0||r>amount)throw new AssertionError("preview/payment reserve parity");
                    }
        for(double fraction:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,1}) {
            try {FundAllocation.reserve(EconomyState.ProsperityFundType.DIRECT_GRANT,EconomyState.DonationPurpose.GENERAL,100,fraction);
                throw new AssertionError("invalid reserve");}catch(IllegalArgumentException expected){}
        }
        System.out.println("PASS FundAllocationRegressionTest");
    }
}
