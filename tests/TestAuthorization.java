package com.e02.rootconsole;
public class TestAuthorization {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){RootState s=new RootState();long first=s.begin();check(s.publish(first,true,true));check(s.available()&&s.bridge());long pending=s.begin();s.clear();check(!s.publish(pending,true,true)&&!s.available());long a=s.begin(),b=s.begin();check(!s.publish(a,true,true));check(s.publish(b,false,true)&&!s.available());long c=s.begin();check(s.publish(c,true,false)&&!s.bridge());RootLaunch.validate(new String[]{"token"});RootLaunch.validate(new String[]{"token","0"});for(String[] bad:new String[][]{{},{"token","600"},{"token","-1"},{"token","nonsense"},{"token","0","extra"}}){boolean rejected=false;try{RootLaunch.validate(bad);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);}System.out.println("Authorization: revocation race, stale result, launch migration PASS");}
}
