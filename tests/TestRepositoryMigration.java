package com.e02.rootconsole;

/** Regression cases for downloads across the planned repository rename. */
public final class TestRepositoryMigration {
 private static void require(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  require(UpdatePolicy.REPO.equals("https://github.com/BerryFuwawa/E02-StarBox"));
  require(UpdatePolicy.MANIFEST.equals("https://raw.githubusercontent.com/BerryFuwawa/E02-StarBox/main/update/stable.json"));
  for(String repo:new String[]{UpdatePolicy.REPO,UpdatePolicy.LEGACY_REPO}){
   String asset=repo+"/releases/download/v1.7.0/E02-Starbox-v1.7.0.apk";
   require(UpdatePolicy.url("",asset).equals(asset));
   require(UpdatePolicy.url("https://example.com",asset).equals("https://example.com/"+asset));
  }
  String prefix=UpdatePolicy.REPO+"/releases/download/";
  String[] rejected={
   "https://github.com.evil.example/BerryFuwawa/E02-StarBox/releases/download/v1.7.0/app.apk",
   "https://user:secret@github.com/BerryFuwawa/E02-StarBox/releases/download/v1.7.0/app.apk",
   "https://github.com:8443/BerryFuwawa/E02-StarBox/releases/download/v1.7.0/app.apk",
   prefix+"v1.7.0/../app.apk",prefix+"v1.7.0/%2e%2e",prefix+"v1.7.0/%2e%2e/app.apk",
   prefix+"v1.7.0/app.apk?redirect=evil",prefix+"v1.7.0/app.apk#fragment",
   prefix+"v1.7.0/",prefix+"/app.apk",prefix+"v1.7.0/subfolder/app.apk",
   "https://github.com/OtherUser/E02-StarBox/releases/download/v1.7.0/app.apk",
   "http://github.com/BerryFuwawa/E02-StarBox/releases/download/v1.7.0/app.apk"
  };
  for(String asset:rejected){try{UpdatePolicy.url("",asset);throw new AssertionError("Unexpected update source: "+asset);}catch(IllegalArgumentException expected){}}
  System.out.println("Repository migration: target/legacy download compatibility and 13 invalid URL cases PASS");
 }
}
