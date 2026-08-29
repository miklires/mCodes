package io.github.miklires.mcodes;

import org.h2.Driver;
import java.nio.file.Path;import java.sql.*;import java.time.*;import java.util.*;import java.util.concurrent.*;

public final class CodeRepository implements AutoCloseable {
    private final String url;private final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mcodes-db-",0).factory());
    public CodeRepository(Path file){this("jdbc:h2:"+file.toAbsolutePath()+";AUTO_SERVER=TRUE");}CodeRepository(String url){this.url=url;}
    public CompletableFuture<Void> initialize(){return run(()->{try(Connection c=open();Statement s=c.createStatement()){
        s.executeUpdate("CREATE TABLE IF NOT EXISTS codes(code VARCHAR(32) PRIMARY KEY,type VARCHAR(16) NOT NULL,owner UUID,max_uses INT NOT NULL,per_player INT NOT NULL,expires_at TIMESTAMP WITH TIME ZONE,redeemer_commands VARCHAR(32000) NOT NULL,owner_commands VARCHAR(32000) NOT NULL,enabled BOOLEAN NOT NULL DEFAULT TRUE,uses INT NOT NULL DEFAULT 0)");
        s.executeUpdate("ALTER TABLE codes ADD COLUMN IF NOT EXISTS per_identity INT NOT NULL DEFAULT 0");s.executeUpdate("ALTER TABLE codes ADD COLUMN IF NOT EXISTS starts_at TIMESTAMP WITH TIME ZONE");s.executeUpdate("ALTER TABLE codes ADD COLUMN IF NOT EXISTS required_permission VARCHAR(100) NOT NULL DEFAULT ''");s.executeUpdate("ALTER TABLE codes ADD COLUMN IF NOT EXISTS min_playtime BIGINT NOT NULL DEFAULT 0");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS redemptions(tx UUID PRIMARY KEY,code VARCHAR(32) NOT NULL,player UUID NOT NULL,player_name VARCHAR(32) NOT NULL,identity_hash VARCHAR(128),created_at TIMESTAMP WITH TIME ZONE NOT NULL,delivered BOOLEAN NOT NULL DEFAULT FALSE)");
        s.executeUpdate("ALTER TABLE redemptions ADD COLUMN IF NOT EXISTS delivery_state VARCHAR(16) NOT NULL DEFAULT 'PENDING'");s.executeUpdate("ALTER TABLE redemptions ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE");s.executeUpdate("UPDATE redemptions SET delivery_state='DELIVERED' WHERE delivered=TRUE AND delivery_state<>'DELIVERED'");
        s.executeUpdate("CREATE INDEX IF NOT EXISTS redemptions_code_player_idx ON redemptions(code,player)");s.executeUpdate("CREATE INDEX IF NOT EXISTS redemptions_identity_idx ON redemptions(code,identity_hash)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS player_identities(player UUID PRIMARY KEY,identity_hash VARCHAR(128) NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS referral_identities(identity_hash VARCHAR(128) PRIMARY KEY,player UUID NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        s.executeUpdate("MERGE INTO referral_identities(identity_hash,player,created_at) KEY(identity_hash) SELECT r.identity_hash,r.player,r.created_at FROM redemptions r JOIN codes c ON c.code=r.code WHERE c.type='REFERRAL' AND r.identity_hash IS NOT NULL AND r.identity_hash<>''");
    }});}
    public CompletableFuture<Boolean> create(CodeDefinition d){return supply(()->{try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO codes(code,type,owner,max_uses,per_player,per_identity,starts_at,expires_at,required_permission,min_playtime,redeemer_commands,owner_commands,enabled,uses) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,TRUE,0)")){p.setString(1,d.code());p.setString(2,d.type().name());p.setObject(3,d.owner());p.setInt(4,d.maxUses());p.setInt(5,d.perPlayer());p.setInt(6,d.perIdentity());p.setObject(7,d.startsAt());p.setObject(8,d.expiresAt());p.setString(9,d.requiredPermission());p.setLong(10,d.minimumPlaytimeSeconds());p.setString(11,String.join("\n",d.redeemerCommands()));p.setString(12,String.join("\n",d.ownerCommands()));return p.executeUpdate()==1;}catch(SQLException e){if("23505".equals(e.getSQLState()))return false;throw e;}});}
    public CompletableFuture<Optional<CodeDefinition>> definition(String raw){return supply(()->{try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM codes WHERE code=?")){p.setString(1,CodeDefinition.normalize(raw));try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}});}
    public CompletableFuture<Boolean> setEnabled(String raw,boolean enabled){return supply(()->{try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE codes SET enabled=? WHERE code=?")){p.setBoolean(1,enabled);p.setString(2,CodeDefinition.normalize(raw));return p.executeUpdate()==1;}});}
    public CompletableFuture<Boolean> disable(String raw){return setEnabled(raw,false);}
    public CompletableFuture<List<String>> list(){return supply(()->{List<String> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT code,type,uses,max_uses,enabled,starts_at,expires_at FROM codes ORDER BY code")){while(r.next())out.add(r.getString(1)+" ["+r.getString(2)+"] "+r.getInt(3)+"/"+(r.getInt(4)==0?"∞":r.getInt(4))+(r.getBoolean(5)?"":" disabled"));}return List.copyOf(out);});}
    public CompletableFuture<RedemptionResult> redeem(String raw, UUID player, String name, String identityHash,
                                                        boolean permissionEligible, long playtimeSeconds) {
        return supply(() -> {
            String code = CodeDefinition.normalize(raw);
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    CodeDefinition definition;
                    int uses;
                    boolean enabled;
                    try (PreparedStatement p = c.prepareStatement("SELECT * FROM codes WHERE code=? FOR UPDATE")) {
                        p.setString(1, code);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) {
                                c.rollback();
                                return RedemptionResult.rejected(RedemptionResult.Status.NOT_FOUND);
                            }
                            definition = map(r);
                            uses = r.getInt("uses");
                            enabled = r.getBoolean("enabled");
                        }
                    }

                    Instant now = Instant.now();
                    RedemptionResult.Status rejection = validate(
                            c, definition, code, player, identityHash, permissionEligible, playtimeSeconds, uses, enabled, now);
                    if (rejection != null) {
                        c.rollback();
                        return RedemptionResult.rejected(rejection);
                    }
                    if (definition.type() == CodeType.REFERRAL
                            && !claimReferralIdentity(c, identityHash, player)) {
                        c.rollback();
                        return RedemptionResult.rejected(RedemptionResult.Status.IDENTITY_LIMIT);
                    }

                    remember(c, player, identityHash);
                    UUID tx = UUID.randomUUID();
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO redemptions(tx,code,player,player_name,identity_hash,created_at,delivered,delivery_state) VALUES(?,?,?,?,?,?,FALSE,'PENDING')")) {
                        p.setObject(1, tx);
                        p.setString(2, code);
                        p.setObject(3, player);
                        p.setString(4, name);
                        p.setString(5, identityHash);
                        p.setObject(6, now);
                        p.executeUpdate();
                    }
                    try (PreparedStatement p = c.prepareStatement("UPDATE codes SET uses=uses+1 WHERE code=?")) {
                        p.setString(1, code);
                        p.executeUpdate();
                    }
                    c.commit();
                    return new RedemptionResult(RedemptionResult.Status.ACCEPTED, tx, definition);
                } catch (Throwable error) {
                    c.rollback();
                    throw error;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        });
    }

    private static RedemptionResult.Status validate(Connection c, CodeDefinition d, String code, UUID player,
                                                      String identityHash, boolean permissionEligible,
                                                      long playtimeSeconds, int uses, boolean enabled,
                                                      Instant now) throws SQLException {
        if (!enabled || d.expiresAt() != null && d.expiresAt().isBefore(now)) return RedemptionResult.Status.EXPIRED;
        if (d.startsAt() != null && d.startsAt().isAfter(now)) return RedemptionResult.Status.NOT_STARTED;
        if (!d.requiredPermission().isBlank() && !permissionEligible) return RedemptionResult.Status.NO_PERMISSION;
        if (playtimeSeconds < d.minimumPlaytimeSeconds()) return RedemptionResult.Status.NOT_ENOUGH_PLAYTIME;
        if (d.owner() != null && d.owner().equals(player)) return RedemptionResult.Status.SELF_REFERRAL;
        if (d.maxUses() > 0 && uses >= d.maxUses()) return RedemptionResult.Status.LIMIT_REACHED;
        if (count(c, "SELECT COUNT(*) FROM redemptions WHERE code=? AND player=?", code, player) >= d.perPlayer()) return RedemptionResult.Status.ALREADY_USED;
        if (d.perIdentity() > 0 && !identityHash.isBlank()
                && count(c, "SELECT COUNT(*) FROM redemptions WHERE code=? AND identity_hash=?", code, identityHash) >= d.perIdentity()) return RedemptionResult.Status.IDENTITY_LIMIT;
        if (d.type() == CodeType.REFERRAL) {
            if (count(c, "SELECT COUNT(*) FROM redemptions x JOIN codes cd ON cd.code=x.code WHERE cd.type='REFERRAL' AND x.player=?", player) > 0) return RedemptionResult.Status.ALREADY_USED;
            if (!identityHash.isBlank() && d.owner() != null
                    && count(c, "SELECT COUNT(*) FROM player_identities WHERE player=? AND identity_hash=?", d.owner(), identityHash) > 0) return RedemptionResult.Status.SAME_IDENTITY;
        }
        return null;
    }
    public CompletableFuture<Void> rememberIdentity(UUID player,String hash){return run(()->{try(Connection c=open()){remember(c,player,hash);}});}
    private static void remember(Connection c,UUID player,String hash)throws SQLException{if(hash==null||hash.isBlank())return;try(PreparedStatement p=c.prepareStatement("MERGE INTO player_identities(player,identity_hash,updated_at) KEY(player) VALUES(?,?,?)")){p.setObject(1,player);p.setString(2,hash);p.setObject(3,Instant.now());p.executeUpdate();}}
    private static boolean claimReferralIdentity(Connection c,String hash,UUID player)throws SQLException{if(hash==null||hash.isBlank())return true;try(PreparedStatement p=c.prepareStatement("INSERT INTO referral_identities(identity_hash,player,created_at) VALUES(?,?,?)")){p.setString(1,hash);p.setObject(2,player);p.setObject(3,Instant.now());p.executeUpdate();return true;}catch(SQLException e){if("23505".equals(e.getSQLState()))return false;throw e;}}
    public CompletableFuture<List<PendingReward>> pending(){return supply(()->rewards("PENDING"));}
    public CompletableFuture<Optional<PendingReward>> claim(UUID tx){return supply(()->{try(Connection c=open()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE redemptions SET delivery_state='CLAIMED',claimed_at=? WHERE tx=? AND delivery_state='PENDING'")){p.setObject(1,Instant.now());p.setObject(2,tx);if(p.executeUpdate()!=1){c.rollback();return Optional.empty();}}PendingReward reward=reward(c,tx).orElseThrow();c.commit();return Optional.of(reward);}});}
    public CompletableFuture<Boolean> delivered(UUID tx){return supply(()->{try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE redemptions SET delivery_state='DELIVERED',delivered=TRUE WHERE tx=? AND delivery_state='CLAIMED'")){p.setObject(1,tx);return p.executeUpdate()==1;}});}
    public CompletableFuture<List<String>> unresolved(){return supply(()->{List<String> out=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT tx,code,player_name,delivery_state,created_at FROM redemptions WHERE delivery_state<>'DELIVERED' ORDER BY created_at");ResultSet r=p.executeQuery()){while(r.next())out.add(r.getObject(1)+" "+r.getString(2)+" "+r.getString(3)+" ["+r.getString(4)+"]");}return List.copyOf(out);});}
    private List<PendingReward> rewards(String state)throws SQLException{List<PendingReward> out=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT r.*,cd.redeemer_commands,cd.owner_commands,cd.owner FROM redemptions r JOIN codes cd ON cd.code=r.code WHERE r.delivery_state=? ORDER BY r.created_at")){p.setString(1,state);try(ResultSet r=p.executeQuery()){while(r.next())out.add(mapReward(r));}}return List.copyOf(out);}
    private static Optional<PendingReward> reward(Connection c,UUID tx)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT r.*,cd.redeemer_commands,cd.owner_commands,cd.owner FROM redemptions r JOIN codes cd ON cd.code=r.code WHERE r.tx=?")){p.setObject(1,tx);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(mapReward(r)):Optional.empty();}}}
    private static PendingReward mapReward(ResultSet r)throws SQLException{return new PendingReward(r.getObject("tx",UUID.class),r.getObject("player",UUID.class),r.getString("player_name"),r.getObject("owner",UUID.class),lines(r.getString("redeemer_commands")),lines(r.getString("owner_commands")));}
    private static long count(Connection c,String sql,Object...args)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}}
    private static CodeDefinition map(ResultSet r)throws SQLException{OffsetDateTime start=r.getObject("starts_at",OffsetDateTime.class),expiry=r.getObject("expires_at",OffsetDateTime.class);return new CodeDefinition(r.getString("code"),CodeType.valueOf(r.getString("type")),r.getObject("owner",UUID.class),r.getInt("max_uses"),r.getInt("per_player"),r.getInt("per_identity"),start==null?null:start.toInstant(),expiry==null?null:expiry.toInstant(),r.getString("required_permission"),r.getLong("min_playtime"),lines(r.getString("redeemer_commands")),lines(r.getString("owner_commands")));}
    private static List<String> lines(String value){return value==null||value.isBlank()?List.of():List.of(value.split("\n"));}
    private Connection open()throws SQLException{Properties p=new Properties();p.setProperty("user","sa");p.setProperty("password","");return new Driver().connect(url,p);}
    private<T>CompletableFuture<T>supply(SqlSupplier<T> task){return CompletableFuture.supplyAsync(()->{try{return task.get();}catch(Exception e){throw new CompletionException(e);}},executor);}private CompletableFuture<Void>run(SqlRunnable task){return supply(()->{task.run();return null;});}
    @Override public void close(){executor.close();}@FunctionalInterface private interface SqlSupplier<T>{T get()throws Exception;}@FunctionalInterface private interface SqlRunnable{void run()throws Exception;}
    public record PendingReward(UUID tx,UUID player,String playerName,UUID owner,List<String>playerCommands,List<String>ownerCommands){}
}
