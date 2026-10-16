package android.content;
public final class ComponentName {
 public String packageName,className;public ComponentName(){}public ComponentName(String packageName,String className){this.packageName=packageName;this.className=className;}
 public static ComponentName unflattenFromString(String value){int slash=value.indexOf('/');if(slash<=0||slash==value.length()-1)return null;String p=value.substring(0,slash),c=value.substring(slash+1);return new ComponentName(p,c.startsWith(".")?p+c:c);}
 @Override public boolean equals(Object other){if(!(other instanceof ComponentName))return false;ComponentName c=(ComponentName)other;return java.util.Objects.equals(packageName,c.packageName)&&java.util.Objects.equals(className,c.className);}
 @Override public int hashCode(){return java.util.Objects.hash(packageName,className);}
}
