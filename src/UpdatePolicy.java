package com.e02.rootconsole;
import java.net.URI;
/** Public GitHub resources only. No credentials are sent to a proxy. */
public final class UpdatePolicy {
 public static final String REPO="https://github.com/BerryFuwawa/E02-StarBox";
 public static final String MANIFEST="https://raw.githubusercontent.com/BerryFuwawa/E02-StarBox/main/update/stable.json";
 public static final String LEGACY_REPO="https://github.com/BerryFuwawa/Galaxy-E02-Starbox";
 public static final String[] ROUTES={"","https://gh-proxy.com","https://gh-proxy.org","https://ghproxy.net","https://githubproxy.cc","https://ghfast.top"};
 public static String normalize(String input){String s=input==null?"":input.trim();if(s.isEmpty())return "";if(!s.contains("://"))s="https://"+s;try{URI u=new URI(s);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception();while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}catch(Exception e){throw new IllegalArgumentException("请输入 HTTPS 加速域名，不含文件路径、账号或查询参数");}}
 public static String url(String proxy,String source){if(!source.equals(MANIFEST)&&!releaseUrl(source))throw new IllegalArgumentException("更新地址不属于星匣官方仓库");return normalize(proxy).isEmpty()?source:normalize(proxy)+"/"+source;}
 private static boolean releaseUrl(String source){try{URI u=new URI(source);String path=u.getPath();if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||path==null||!u.normalize().getPath().equals(path))return false;String prefix="/BerryFuwawa/";if(!(path.startsWith(prefix+"E02-StarBox/releases/download/")||path.startsWith(prefix+"Galaxy-E02-Starbox/releases/download/")))return false;String[] parts=path.split("/",-1);if(parts.length!=7)return false;for(String part:parts)if(part.equals(".")||part.equals("..")||part.contains("\\"))return false;return !parts[5].isEmpty()&&!parts[6].isEmpty();}catch(Exception e){return false;}}
 public static boolean newer(long remote,long local){return remote>local;}
}
