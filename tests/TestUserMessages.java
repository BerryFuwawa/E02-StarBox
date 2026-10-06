package com.e02.rootconsole;
public final class TestUserMessages {
 public static void main(String[] args){
  String[] raw={"Connection refused","connect timed out","Unable to resolve host example.org","Permission denied","Invalid bridge output length","ENOENT /data/user/0/private","SSLHandshakeException: certificate","IOException: read failed"};
  for(String s:raw){String shown=UserMessages.explain(s);if(shown.equals(s)||shown.contains("Exception")||shown.contains("/data/"))throw new AssertionError(shown);}
  String validation="端口须为 1–65535";if(!validation.equals(UserMessages.explain(validation)))throw new AssertionError("validation lost");
  if(UserMessages.explain((String)null).isEmpty())throw new AssertionError("missing message");
  System.out.println("User-facing errors: 10 checks passed");
 }
}
