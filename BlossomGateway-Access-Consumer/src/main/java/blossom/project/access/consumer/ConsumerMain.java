package blossom.project.access.consumer;
import blossom.project.access.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class ConsumerMain {
    private static String required(String property,String environment) {
        String value=System.getProperty(property,System.getenv(environment));
        if(value==null || value.isBlank()) throw new IllegalStateException("Configure "+environment);
        return value;
    }
    public static void main(String[] args) throws Exception {
        String url=System.getProperty("access.dbUrl",System.getenv().getOrDefault("AEGIS_DB_URL","jdbc:mysql://127.0.0.1:3306/aegisflow_access?useAffectedRows=true&connectTimeout=1000&socketTimeout=1500"));
        AccessStore store=new AccessStore(url,required("access.dbUser","AEGIS_DB_USER"),required("access.dbPassword","AEGIS_DB_PASSWORD"));
        AccessConsumer consumer=new AccessConsumer(RabbitTopology.factory(
            System.getProperty("access.rabbitHost",System.getenv().getOrDefault("AEGIS_RABBIT_HOST","127.0.0.1")),
            Integer.getInteger("access.rabbitPort",5672),required("access.rabbitUser","AEGIS_RABBIT_USER"),required("access.rabbitPassword","AEGIS_RABBIT_PASSWORD")),store);
        HttpServer server=HttpServer.create(new InetSocketAddress(System.getProperty("access.statsHost","127.0.0.1"),8094),0);
        var executor=Executors.newSingleThreadExecutor(); server.setExecutor(executor);
        ObjectMapper mapper=new ObjectMapper();
        server.createContext("/stats",exchange->{
            int status=200; Map<String,Object> data;
            try {
                String query=exchange.getRequestURI().getRawQuery();
                String id=query!=null && query.startsWith("eventId=")?query.substring(8):null;
                data=Map.of("rows",store.count(id),"consumed",consumer.consumed.get(),"duplicates",consumer.duplicates.get(),"retries",consumer.retries.get(),"failed",consumer.failed.get(),"errors",consumer.errors.get(),"committedUnacked",consumer.committedUnacked.get());
            } catch(Exception failure) { status=503; data=Map.of("error","database_unavailable","retries",consumer.retries.get(),"failed",consumer.failed.get()); }
            byte[] bytes=mapper.writeValueAsBytes(data); exchange.getResponseHeaders().set("Content-Type","application/json;charset=utf-8");
            exchange.sendResponseHeaders(status,bytes.length); try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        server.createContext("/prometheus",exchange->{
            StringBuilder text=new StringBuilder();
            for(var entry:Map.of("messages",consumer.consumed,"duplicates",consumer.duplicates,"retries",consumer.retries,"failed",consumer.failed,"errors",consumer.errors).entrySet()) {
                text.append("access_consumer_").append(entry.getKey()).append("_total ").append(entry.getValue().get()).append('\n');
            }
            text.append("access_consumer_committed_unacked ").append(consumer.committedUnacked.get()).append('\n');
            try { text.append("access_database_rows ").append(store.count(null)).append("\naccess_database_up 1\n"); }
            catch(Exception failure) { text.append("access_database_up 0\n"); }
            byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200,bytes.length); try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        consumer.start(); server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(0);consumer.close();executor.shutdownNow();}));
        System.out.println("Access consumer stats: http://127.0.0.1:8094/stats");
    }
}
