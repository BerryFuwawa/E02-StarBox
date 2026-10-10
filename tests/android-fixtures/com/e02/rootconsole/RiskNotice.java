package com.e02.rootconsole;
/** Only the first-use gate is mocked; production RiskNotice UI is checked by the separate API28 compile. */
public final class RiskNotice {
    public static boolean accepted(android.content.Context context){return context.getSharedPreferences("risk_notice",0).getInt("acceptedVersion",0)>=1;}
}
