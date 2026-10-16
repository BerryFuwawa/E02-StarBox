package com.e02.rootconsole;
public final class TestDisplayWakeGate {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("display wake "+checks);}
 public static void main(String[] args){
  DisplayWakeGate gate=new DisplayWakeGate();gate.prime(2);
  check(!gate.event(0,2,2));check(!gate.event(1,2,1));check(!gate.event(1,2,2));
  check(!gate.event(0,2,1));check(gate.event(0,2,2));check(!gate.event(0,2,2));
  check(!gate.event(0,2,3));check(gate.event(0,2,2));
  check(!gate.event(0,3,0));check(!gate.event(0,1,2));check(!gate.event(0,2,2));
  check(!gate.event(0,2,0));check(!gate.event(0,2,2));
  check(!gate.event(0,2,1));check(!gate.event(0,77,2));check(gate.event(0,2,2));
  gate.close();check(!gate.event(0,2,1));check(!gate.event(0,2,2));gate.prime(1);check(!gate.event(0,2,2));
  gate=new DisplayWakeGate();gate.prime(1);check(gate.event(0,2,2));
  for(int i=0;i<100;i++)check(!gate.event(0,2,2));
  check(gate.isOn());gate.event(0,2,1);check(!gate.isOn());gate.close();check(!gate.isOn());
  System.out.println("PASS "+checks+" display-transition checks; actual vehicle callback delivery remains pending.");
 }
}
