package com.e02.rootconsole;
import android.content.Context;
import android.net.*;
import java.net.*;
import org.json.JSONObject;
/** Linux frpc cannot read Android's per-network DNS settings from resolv.conf. */
public final class RemoteDns {
 public static String adapt(Context c,String config,String format){try{ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);LinkProperties properties=manager.getLinkProperties(manager.getActiveNetwork());if(properties==null||properties.getDnsServers().isEmpty())return config;InetAddress chosen=properties.getDnsServers().get(0);for(InetAddress a:properties.getDnsServers())if(a instanceof Inet4Address){chosen=a;break;}String ip=chosen.getHostAddress();String dns=chosen instanceof Inet6Address?"["+ip+"]:53":ip+":53";if(format.equals("json")){JSONObject j=new JSONObject(config);if(!j.has("dnsServer"))j.put("dnsServer",dns);return j.toString(2);}if(format.equals("yaml")){if(!java.util.regex.Pattern.compile("(?m)^dnsServer\\s*:").matcher(config).find())return "dnsServer: "+RemoteConfig.quoted(dns)+"\n"+config;}else if(!java.util.regex.Pattern.compile("(?m)^\\s*dnsServer\\s*=").matcher(config).find())return "dnsServer = "+RemoteConfig.quoted(dns)+"\n"+config;}catch(Exception ignored){}return config;}
}
