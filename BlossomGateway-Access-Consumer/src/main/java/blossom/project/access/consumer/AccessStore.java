package blossom.project.access.consumer;
import blossom.project.access.AccessEvent;
import java.sql.*;
import java.util.List;

public final class AccessStore {
    private final String url,user,password;
    public AccessStore(String url,String user,String password) { this.url=url;this.user=user;this.password=password; }
    public int persist(List<AccessEvent> events) throws SQLException {
        try(Connection connection=DriverManager.getConnection(url,user,password)) {
            connection.setAutoCommit(false);
            try(PreparedStatement statement=connection.prepareStatement("INSERT INTO access_event(event_id,request_id,gateway_id,route_id,service_id,instance_id,method,status,outcome,reason,duration_ms,completed_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_id=event_id")) {
                for(AccessEvent event:events) {
                    statement.setString(1,event.eventId()); statement.setString(2,event.requestId()); statement.setString(3,event.gatewayId());
                    statement.setString(4,event.routeId()); statement.setString(5,event.serviceId()); statement.setString(6,event.instanceId());
                    statement.setString(7,event.method()); statement.setInt(8,event.status()); statement.setString(9,event.outcome()); statement.setString(10,event.reason());
                    statement.setLong(11,event.durationMs()); statement.setLong(12,event.completedAtMs()); statement.addBatch();
                }
                int inserted=0; for(int result:statement.executeBatch()) { if(result>0) { inserted++; } }
                connection.commit(); return inserted;
            } catch(SQLException error) { connection.rollback(); throw error; }
        }
    }
    public long count(String eventId) throws SQLException {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            PreparedStatement statement=connection.prepareStatement(eventId==null?"SELECT COUNT(*) FROM access_event":"SELECT COUNT(*) FROM access_event WHERE event_id=?")) {
            if(eventId!=null) { statement.setString(1,eventId); }
            try(ResultSet result=statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }
}
