package com.e02.rootconsole;
import java.io.IOException;
import java.util.*;
public final class TestMirrorSearch {
 public static void main(String[] args){String[] routes={"","one","two","three"};List<String> visited=new ArrayList<>();String result=MirrorSearch.first(routes,new MirrorSearch.Probe(){public boolean cancelled(){return false;}public void testing(int i,String route){}public void verify(String route)throws Exception{visited.add(route);if(!route.equals("two"))throw new IOException();}});if(!result.equals("two")||!visited.equals(Arrays.asList("one","two")))throw new AssertionError("stop at first success in order");
  visited.clear();result=MirrorSearch.first(routes,new MirrorSearch.Probe(){public boolean cancelled(){return !visited.isEmpty();}public void testing(int i,String route){}public void verify(String route)throws Exception{visited.add(route);throw new IOException();}});if(!result.isEmpty()||visited.size()!=1)throw new AssertionError("cancel prevents next mirror");
  visited.clear();result=MirrorSearch.first(routes,new MirrorSearch.Probe(){public boolean cancelled(){return false;}public void testing(int i,String route){}public void verify(String route)throws Exception{visited.add(route);throw new IOException();}});if(!result.isEmpty()||visited.size()!=3)throw new AssertionError("all failed");System.out.println("Mirror search: order, first success, cancellation, all-failed PASS");
 }
}
