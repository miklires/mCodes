package io.github.miklires.mcodes;

import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class MCodesPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private CodeRepository repository;
    private volatile boolean ready;

    @Override public void onEnable() {
        saveDefaultConfig();
        repository=new CodeRepository(getDataFolder().toPath().resolve("codes"));
        repository.initialize().thenRun(()->{ready=true;retryPending();getLogger().info("mCodes 1.0.0 is ready");})
            .exceptionally(e->{getLogger().severe("Storage failed: "+root(e).getMessage());return null;});
        for(String name:List.of("code","refer")){PluginCommand command=Objects.requireNonNull(getCommand(name));command.setExecutor(this);command.setTabCompleter(this);}
        new Metrics(this,27942);
    }
    @Override public void onDisable(){if(repository!=null)repository.close();}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!ready){sender.sendMessage("§cХранилище ещё запускается.");return true;}
        if(command.getName().equals("refer"))return refer(sender,args);
        if(args.length==0){sender.sendMessage("§e/code redeem <код>");return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "redeem"->{if(!(sender instanceof Player player)||args.length<2){sender.sendMessage("§c/code redeem <код>");return true;}redeem(player,args[1]);}
            case "list"->{if(!admin(sender))return true;repository.list().thenAccept(lines->sync(()->sender.sendMessage(lines.isEmpty()?"§7Кодов нет.":String.join("\n",lines))));}
            case "create"->{if(!admin(sender)||args.length<3){sender.sendMessage("§c/code create <код> <лимит>");return true;}try{int limit=Integer.parseInt(args[2]);var d=new CodeDefinition(args[1],CodeType.PROMO,null,limit,1,null,getConfig().getStringList("promo-reward-commands"),List.of());repository.create(d).thenAccept(ok->sync(()->sender.sendMessage(ok?"§aКод создан.":"§cКод уже существует.")));}catch(Exception e){sender.sendMessage("§c"+e.getMessage());}}
            case "delete"->{if(!admin(sender)||args.length<2){sender.sendMessage("§c/code delete <код>");return true;}repository.disable(args[1]).thenAccept(ok->sync(()->sender.sendMessage(ok?"§aКод отключён.":"§cКод не найден.")));}
            case "reload"->{if(!admin(sender))return true;reloadConfig();sender.sendMessage("§aКонфигурация перезагружена.");}
            default->sender.sendMessage("§c/code <redeem|create|delete|list|reload>");
        }return true;
    }
    private boolean refer(CommandSender sender,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("§cТолько для игроков.");return true;}
        if(args.length>0){redeem(player,args[0]);return true;}
        String code=CodeDefinition.normalize(player.getName());
        var d=new CodeDefinition(code,CodeType.REFERRAL,player.getUniqueId(),0,1,null,getConfig().getStringList("referral-redeemer-commands"),getConfig().getStringList("referral-owner-commands"));
        repository.create(d).thenAccept(ignored->sync(()->player.sendMessage("§aВаш реферальный код: §f"+code)));return true;
    }
    private void redeem(Player player,String code){String address=player.getAddress()==null?"":player.getAddress().getAddress().getHostAddress();repository.redeem(code,player.getUniqueId(),player.getName(),hash(address)).thenAccept(result->sync(()->{if(result.status()!=RedemptionResult.Status.ACCEPTED){player.sendMessage("§cКод не применён: "+result.status());return;}deliver(new CodeRepository.PendingReward(result.transactionId(),player.getUniqueId(),player.getName(),result.definition().owner(),result.definition().redeemerCommands(),result.definition().ownerCommands()));}));}
    private void retryPending(){repository.pending().thenAccept(list->sync(()->list.forEach(this::deliver)));}
    private void deliver(CodeRepository.PendingReward reward){String owner=reward.owner()==null?"":Optional.ofNullable(Bukkit.getOfflinePlayer(reward.owner()).getName()).orElse("");reward.playerCommands().forEach(c->dispatch(c,reward.playerName(),owner));if(!owner.isBlank())reward.ownerCommands().forEach(c->dispatch(c,owner,reward.playerName()));repository.delivered(reward.tx());Player player=Bukkit.getPlayer(reward.player());if(player!=null)player.sendMessage("§aКод успешно применён.");}
    private void dispatch(String raw,String player,String other){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),raw.replace("{player}",player).replace("{referrer}",other).replace("{redeemer}",other));}
    private boolean admin(CommandSender sender){if(sender.hasPermission("mcodes.admin"))return true;sender.sendMessage("§cНедостаточно прав.");return false;}
    private void sync(Runnable task){getServer().getGlobalRegionScheduler().execute(this,task);}
    private static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(command.getName().equals("code")&&args.length==1)return List.of("redeem","create","delete","list","reload").stream().filter(v->v.startsWith(args[0].toLowerCase())).toList();return List.of();}
}
