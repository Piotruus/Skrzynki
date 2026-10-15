package pl.example.skrzynki;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public final class SkrzynkiPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey keyId;
    private NamespacedKey crateItemId;
    private final Map<UUID,String> animating = new HashMap<>();
    private final Map<UUID,ItemStack> lastReward = new HashMap<>();
    private final Map<UUID,String> resultCrate = new HashMap<>();
    private final Set<UUID> editors = new HashSet<>();
    private final Set<UUID> opening = new HashSet<>();
    private final Map<UUID, Long> openStarted = new HashMap<>();
    private final Random random = new Random();
    private org.bukkit.configuration.file.FileConfiguration stats;
    private java.io.File statsFile;

    private static final class CaseHolder implements InventoryHolder {
        final String id; final String type; Inventory inv;
        CaseHolder(String id,String type){this.id=id;this.type=type;}
        @Override public Inventory getInventory(){return inv;}
    }
    @Override public void onEnable(){saveDefaultConfig();keyId=new NamespacedKey(this,"case_key");crateItemId=new NamespacedKey(this,"crate_item");statsFile=new java.io.File(getDataFolder(),"stats.yml");if(!statsFile.exists())try{getDataFolder().mkdirs();statsFile.createNewFile();}catch(java.io.IOException ex){getLogger().warning("Nie można utworzyć stats.yml: "+ex.getMessage());}stats=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(statsFile);getServer().getPluginManager().registerEvents(this,this);if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI")!=null){new SkrzynkiExpansion(this).register();getLogger().info("PlaceholderAPI expansion registered");}Objects.requireNonNull(getCommand("case")).setExecutor(this);Objects.requireNonNull(getCommand("case")).setTabCompleter(this);Objects.requireNonNull(getCommand("skrzynki")).setExecutor(this);Bukkit.getScheduler().runTask(this,this::refreshHolograms);getLogger().info("Skrzynki enabled");}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private String path(String id,String suffix){return "crates."+id+"."+suffix;}
    private Set<String> crateIds(){ConfigurationSection s=getConfig().getConfigurationSection("crates");return s==null?new HashSet<>():s.getKeys(false);}
    private void msg(CommandSender s,String t){s.sendMessage(color("&6&lCASE &8» &7"+t));}
    private boolean admin(CommandSender s){if(s.hasPermission("skrzynki.admin"))return true;msg(s,"Nie masz uprawnienia &cskrzynki.admin&7.");return false;}
    private ItemStack named(Material mat,String name,String... lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(color(name));m.setLore(Arrays.stream(lore).map(this::color).toList());i.setItemMeta(m);return i;}
    private ItemStack keyItem(String id,int amount){Material mat=Material.matchMaterial(getConfig().getString(path(id,"key-material"),"TRIPWIRE_HOOK"));if(mat==null)mat=Material.TRIPWIRE_HOOK;ItemStack i=new ItemStack(mat,Math.max(1,Math.min(64,amount)));ItemMeta m=i.getItemMeta();m.setDisplayName(color(getConfig().getString(path(id,"key-name"),"&6Klucz: "+id)));m.setLore(List.of(color("&8Klucz do skrzynki"),color("&7Skrzynka: &f"+getConfig().getString(path(id,"display-name"),id))));m.getPersistentDataContainer().set(keyId,PersistentDataType.STRING,id);i.setItemMeta(m);return i;}
    private String keyOf(ItemStack i){return i==null||!i.hasItemMeta()?null:i.getItemMeta().getPersistentDataContainer().get(keyId,PersistentDataType.STRING);}
    private ItemStack crateItem(String id,int amount){
        ItemStack item=new ItemStack(Material.CHEST,Math.max(1,Math.min(64,amount)));
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString(path(id,"display-name"),"&6Skrzynka "+id)));
        meta.setLore(List.of(color("&7Postaw, aby utworzyć skrzynkę"),color("&8Typ: "+id)));
        meta.getPersistentDataContainer().set(crateItemId,PersistentDataType.STRING,id);
        item.setItemMeta(meta);return item;
    }
    private String crateItemOf(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(crateItemId,PersistentDataType.STRING);}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){if(c.getName().equalsIgnoreCase("skrzynki")){msg(s,"Użyj &e/case help&7.");return true;}if(a.length==0||a[0].equalsIgnoreCase("help")){msg(s,"/case create <nazwa>, get <nazwa> [ilość], edit <nazwa>, setlocation <nazwa>, removelocation <nazwa>, delete <nazwa>, give <gracz> <nazwa> [ilość], giveall <nazwa> [ilość], chance <nazwa> <slot> <procent>, list, reload");return true;}String sub=a[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "list" -> msg(s,"Skrzynki: &f"+String.join(", ",crateIds()));
            case "stats" -> {if(!(s instanceof Player p)){msg(s,"Ta komenda jest tylko dla gracza.");return true;}msg(p,"Otwarte skrzynki: &e"+stats.getInt("players."+p.getUniqueId()+".opened",0)+"&7, ostatnia nagroda: &f"+stats.getString("players."+p.getUniqueId()+".last-reward","brak"));}
            case "reload" -> {if(!admin(s))return true;reloadConfig();refreshHolograms();msg(s,"Konfiguracja przeładowana.");}
            case "create" -> {if(!admin(s))return true;if(a.length<2){msg(s,"Użycie: /case create <nazwa>");return true;}String id=a[1].toLowerCase(Locale.ROOT);if(crateIds().contains(id)){msg(s,"Ta skrzynka już istnieje.");return true;}getConfig().set(path(id,"display-name"),"&6Skrzynka "+id);getConfig().set(path(id,"key-material"),"TRIPWIRE_HOOK");getConfig().set(path(id,"key-name"),"&6Klucz "+id);getConfig().set(path(id,"hologram"),defaultHologram(id));getConfig().set(path(id,"rewards"),new ArrayList<>());saveConfig();msg(s,"Utworzono skrzynkę &f"+id+"&7. Dodaj nagrody: &e/case edit "+id);}
            case "chance" -> {if(!admin(s))return true;if(a.length<4){msg(s,"Użycie: /case chance <nazwa> <slot 1-45> <procent>");return true;}String id=a[1].toLowerCase(Locale.ROOT);if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}int slot;double pct;try{slot=Integer.parseInt(a[2]);pct=Double.parseDouble(a[3].replace(',','.'));}catch(Exception ex){msg(s,"Slot i procent muszą być liczbami.");return true;}if(slot<1||slot>45||pct<0||pct>100){msg(s,"Slot: 1-45, szansa: 0-100.");return true;}List<ItemStack> list=rewards(id);if(slot>list.size()){msg(s,"W tym slocie nie ma nagrody.");return true;}ItemStack item=list.get(slot-1);ItemMeta meta=item.getItemMeta();List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();lore.removeIf(line->{String plain=ChatColor.stripColor(line);return plain!=null&&plain.toLowerCase(Locale.ROOT).matches(".*szansa\\s*:.*");});lore.add(color("&7Szansa: &e"+String.format(Locale.US,"%.4f",pct).replaceAll("0+$","").replaceAll("\\.$","")+"%"));meta.setLore(lore);item.setItemMeta(meta);list.set(slot-1,item);getConfig().set(path(id,"rewards"),list);saveConfig();msg(s,"Ustawiono szansę nagrody w slocie "+slot+" na "+pct+"%. Suma szans musi wynosić maks. 100%, jeśli chcesz zachować pozostałą szansę na brak dropu.");}
            case "get" -> {if(!admin(s))return true;if(!(s instanceof Player p)||a.length<2){msg(s,"Użycie: /case get <nazwa> [ilość]");return true;}String id=a[1].toLowerCase(Locale.ROOT);if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}int n=parseInt(a.length>2?a[2]:"1",1);give(p,crateItem(id,n));msg(s,"Otrzymano przedmiot skrzynki &f"+id+"&7. Postaw go na mapie.");}
            case "edit" -> {if(!admin(s))return true;if(!(s instanceof Player p)||a.length<2){msg(s,"Użycie: /case edit <nazwa> (jako gracz)");return true;}String id=a[1].toLowerCase(Locale.ROOT);if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}openEditor(p,id);}
            case "delete" -> {if(!admin(s)||a.length<2)return true;removeHolograms(a[1]);getConfig().set("crates."+a[1],null);saveConfig();msg(s,"Usunięto skrzynkę &f"+a[1]);}
            case "setlocation" -> {if(!admin(s))return true;if(!(s instanceof Player p)||a.length<2){msg(s,"Użycie: /case setlocation <nazwa> (jako gracz)");return true;}String id=a[1].toLowerCase(Locale.ROOT);if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}Block b=p.getTargetBlockExact(6);if(b==null){msg(s,"Najpierw spójrz na blok skrzynki.");return true;}getConfig().set(path(id,"location.world"),b.getWorld().getName());getConfig().set(path(id,"location.x"),b.getX());getConfig().set(path(id,"location.y"),b.getY());getConfig().set(path(id,"location.z"),b.getZ());saveConfig();refreshHolograms();msg(s,"Ustawiono lokalizację skrzynki &f"+id);}
            case "removelocation" -> {if(!admin(s)||a.length<2)return true;removeHolograms(a[1]);getConfig().set(path(a[1],"location"),null);saveConfig();msg(s,"Usunięto lokalizację skrzynki &f"+a[1]);}
            case "give" -> {if(!admin(s))return true;if(a.length<3){msg(s,"Użycie: /case give <gracz> <nazwa> [ilość]");return true;}Player p=Bukkit.getPlayerExact(a[1]);String id=a[2].toLowerCase(Locale.ROOT);int n=parseInt(a.length>3?a[3]:"1",1);if(p==null){msg(s,"Gracz musi być online.");return true;}if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}give(p,keyItem(id,n));msg(s,"Dano klucze graczowi &f"+p.getName()+"&7.");}
            case "giveall" -> {if(!admin(s))return true;if(a.length<2)return true;String id=a[1].toLowerCase(Locale.ROOT);if(!crateIds().contains(id)){msg(s,"Nie ma takiej skrzynki.");return true;}int n=parseInt(a.length>2?a[2]:"1",1);for(Player p:Bukkit.getOnlinePlayers())give(p,keyItem(id,n));msg(s,"Rozdano klucze wszystkim online.");}
            default -> msg(s,"Nieznana komenda. &e/case help");
        }return true;}
    private List<String> defaultHologram(String id){
        String name=switch(id.toLowerCase(Locale.ROOT)){case "flowybox"->"&b&lꜰʟᴏᴡʏʙᴏx";case "rzadka"->"&a&lʀᴢᴀᴅᴋᴀ";case "epicka"->"&5&lᴇᴘɪᴄᴋᴀ";case "legendarna"->"&6&lʟᴇɢᴇɴᴅᴀʀɴᴀ";default->"&6&l"+id.toUpperCase(Locale.ROOT);};
        return List.of(name,"&fᴋʟɪᴋɴɪᴊ &a[ᴘᴘᴍ] &fᴀʙʏ ᴏᴛᴡᴏʀᴢʏᴄ!","&fᴋʟᴜᴄᴢ ᴢᴀᴋᴜᴘɪꜱᴢ ɴᴀ &bᴡᴡᴡ.ꜰʟᴏᴡʏᴍᴄ.ᴘʟ");
    }
    private int parseInt(String s,int d){try{return Math.max(1,Math.min(64,Integer.parseInt(s)));}catch(Exception e){return d;}}
    private void give(Player p,ItemStack i){Map<Integer,ItemStack> left=p.getInventory().addItem(i);left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));}
    private List<ItemStack> rewards(String id){List<?> raw=getConfig().getList(path(id,"rewards"),new ArrayList<>());List<ItemStack> out=new ArrayList<>();for(Object o:raw)if(o instanceof ItemStack i&&i.getType()!=Material.AIR)out.add(i.clone());return out;}
    private void saveRewards(String id,Inventory inv){List<ItemStack> items=new ArrayList<>();for(int slot=0;slot<45;slot++){ItemStack i=inv.getItem(slot);if(i!=null&&i.getType()!=Material.AIR)items.add(i.clone());}getConfig().set(path(id,"rewards"),items);saveConfig();}
    private void openEditor(Player p,String id){CaseHolder h=new CaseHolder(id,"editor");h.inv=Bukkit.createInventory(h,54,color("&8Edytor dropu: "+getConfig().getString(path(id,"display-name"),id)));List<ItemStack> list=rewards(id);for(int n=0;n<Math.min(45,list.size());n++)h.inv.setItem(n,list.get(n));for(int n=45;n<54;n++)h.inv.setItem(n,named(Material.GRAY_STAINED_GLASS_PANE," "));h.inv.setItem(49,named(Material.EMERALD_BLOCK,"&a&lZAPISZ DROP","&7Kliknij, aby zapisać nagrody"));p.openInventory(h.inv);editors.add(p.getUniqueId());msg(p,"Włóż nagrody do slotów 1-45. Kliknij ZAPISZ, aby zachować drop.");}
    private void openPreview(Player p,String id){CaseHolder h=new CaseHolder(id,"preview");h.inv=Bukkit.createInventory(h,54,color(getConfig().getString(path(id,"gui-title"),"&8"+getConfig().getString(path(id,"display-name"),id))));List<ItemStack> list=rewards(id);for(int n=0;n<Math.min(45,list.size());n++)h.inv.setItem(n,list.get(n));for(int n=45;n<54;n++)h.inv.setItem(n,named(Material.BLACK_STAINED_GLASS_PANE," "));h.inv.setItem(45,named(Material.BARRIER,"&c&lZamknij"));h.inv.setItem(49,named(Material.TRIPWIRE_HOOK,"&e&lOtwórz bez animacji","&7Natychmiastowe losowanie"));h.inv.setItem(53,named(Material.FIREWORK_ROCKET,"&b&lOtwórz z animacją","&7Losowanie z efektami"));p.openInventory(h.inv);}
    private void openResult(Player p,String id,ItemStack reward){CaseHolder h=new CaseHolder(id,"result");h.inv=Bukkit.createInventory(h,27,color("&8Wynik otwierania"));h.inv.setItem(13,reward.clone());h.inv.setItem(18,named(Material.BARRIER,"&c&lZamknij"));h.inv.setItem(26,named(Material.LIME_DYE,"&a&lKolejna skrzynka","&7Otwórz ponownie"));p.openInventory(h.inv);}
    private double rewardChance(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return -1;
        for (String line : item.getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bSzansa\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?").matcher(plain);
            if (m.find()) try { return Math.max(0, Math.min(100, Double.parseDouble(m.group(1).replace(',', '.')))); } catch (Exception ignored) {}
        }
        return -1;
    }
    private ItemStack choose(String id){
        List<ItemStack> list=rewards(id); if(list.isEmpty())return null;
        double total=0; boolean configured=false;
        for(ItemStack i:list){double c=rewardChance(i);if(c>=0){configured=true;total+=c;}}
        if(!configured)return list.get(random.nextInt(list.size())).clone();
        // Each configured chance is an actual percentage. The remaining probability produces no reward.
        double roll=random.nextDouble()*100.0, cursor=0;
        for(ItemStack i:list){double c=rewardChance(i);if(c<0)continue;cursor+=c;if(roll<cursor)return i.clone();}
        return null;
    }
    private boolean consumeKey(Player p,String id){ItemStack hand=p.getInventory().getItemInMainHand();if(id.equals(keyOf(hand))){if(hand.getAmount()>1)hand.setAmount(hand.getAmount()-1);else p.getInventory().setItemInMainHand(null);return true;}ItemStack off=p.getInventory().getItemInOffHand();if(id.equals(keyOf(off))){if(off.getAmount()>1)off.setAmount(off.getAmount()-1);else p.getInventory().setItemInOffHand(null);return true;}msg(p,"Potrzebujesz klucza do tej skrzynki.");return false;}
    private void beginOpen(Player p,String id,boolean animate){if(opening.contains(p.getUniqueId())||animating.containsKey(p.getUniqueId()))return;ItemStack prize=choose(id);if(prize==null){msg(p,"Brak nagrody w tym losowaniu (sprawdź szanse w /case edit "+id+"). Klucz nie został zużyty.");return;}if(!consumeKey(p,id))return;opening.add(p.getUniqueId());openStarted.put(p.getUniqueId(),System.nanoTime());resultCrate.put(p.getUniqueId(),id);recordOpen(p,id,prize);give(p,prize.clone());if(!animate){lastReward.put(p.getUniqueId(),prize);if(getConfig().getBoolean("settings.sounds",true))p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1,1.2f);if(getConfig().getBoolean("settings.particles",true))p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),18,.4,.5,.4,.05);openResult(p,id,prize);return;}animating.put(p.getUniqueId(),id);CaseHolder h=new CaseHolder(id,"animation");h.inv=Bukkit.createInventory(h,27,color("&8Losowanie nagrody..."));p.openInventory(h.inv);new BukkitRunnable(){int tick=0;public void run(){if(!p.isOnline()){animating.remove(p.getUniqueId());opening.remove(p.getUniqueId());openStarted.remove(p.getUniqueId());cancel();return;}int delay=tick<12?2:(tick<18?3:5);if(tick%delay==0){ItemStack shown=choose(id);if(shown!=null)h.inv.setItem(13,shown);if(getConfig().getBoolean("settings.sounds",true))p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.45f,1.0f+Math.min(tick,20)*.035f);}tick++;if(tick>=30){cancel();animating.remove(p.getUniqueId());lastReward.put(p.getUniqueId(),prize);openStarted.remove(p.getUniqueId());if(p.isOnline()){if(getConfig().getBoolean("settings.sounds",true))p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1,1.2f);openResult(p,id,prize);}else opening.remove(p.getUniqueId());}}}.runTaskTimer(this,0,1);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeCrate(org.bukkit.event.block.BlockPlaceEvent e){
        String id=crateItemOf(e.getItemInHand());if(id==null)return;
        if(!crateIds().contains(id)){e.setCancelled(true);return;}
        Block b=e.getBlockPlaced();String base=path(id,"location");
        String oldWorld=getConfig().getString(base+".world");
        if(oldWorld!=null){msg(e.getPlayer(),"Ta skrzynka ma już lokalizację. Usuń ją najpierw: /case removelocation "+id);e.setCancelled(true);return;}
        getConfig().set(base+".world",b.getWorld().getName());getConfig().set(base+".x",b.getX());getConfig().set(base+".y",b.getY());getConfig().set(base+".z",b.getZ());saveConfig();
        Bukkit.getScheduler().runTask(this,this::refreshHolograms);msg(e.getPlayer(),"Postawiono skrzynkę &f"+id+"&7. PPM otwiera jej menu.");
    }
    @EventHandler public void interact(PlayerInteractEvent e){if(e.getAction()!=Action.RIGHT_CLICK_BLOCK&&e.getAction()!=Action.LEFT_CLICK_BLOCK)return;Block b=e.getClickedBlock();if(b==null)return;for(String id:crateIds()){if(!getConfig().getString(path(id,"location.world"),"").equals(b.getWorld().getName()))continue;if(getConfig().getInt(path(id,"location.x"),Integer.MIN_VALUE)==b.getX()&&getConfig().getInt(path(id,"location.y"),Integer.MIN_VALUE)==b.getY()&&getConfig().getInt(path(id,"location.z"),Integer.MIN_VALUE)==b.getZ()){e.setCancelled(true);openPreview(e.getPlayer(),id);return;}}}
    @EventHandler public void inventoryClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory().getHolder(false) instanceof CaseHolder h))return;if(h.type.equals("editor")){if(e.getClickedInventory()==e.getView().getTopInventory()){int slot=e.getRawSlot();if(slot>=45){e.setCancelled(true);if(slot==49){saveRewards(h.id,h.inv);editors.remove(p.getUniqueId());p.closeInventory();msg(p,"Zapisano drop skrzynki &f"+h.id);}}}return;}e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;int slot=e.getRawSlot();if(h.type.equals("preview")){if(slot==45)p.closeInventory();else if(slot==49||slot==53){boolean animate=slot==53;p.closeInventory();beginOpen(p,h.id,animate);}}else if(h.type.equals("result")){if(slot==18){p.closeInventory();resultCrate.remove(p.getUniqueId());opening.remove(p.getUniqueId());openStarted.remove(p.getUniqueId());}else if(slot==26){String id=h.id;p.closeInventory();opening.remove(p.getUniqueId());openStarted.remove(p.getUniqueId());beginOpen(p,id,true);}}}
    @EventHandler public void inventoryClose(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p)||!(e.getView().getTopInventory().getHolder(false) instanceof CaseHolder h))return;if(h.type.equals("animation")){ /* animation task owns the lock; closing the view must not unlock it */ }if(h.type.equals("result")){opening.remove(p.getUniqueId());}if(h.type.equals("editor")){saveRewards(h.id,h.inv);editors.remove(p.getUniqueId());}}
    private void recordOpen(Player p,String id,ItemStack reward){String base="players."+p.getUniqueId();stats.set(base+".name",p.getName());stats.set(base+".opened",stats.getInt(base+".opened",0)+1);stats.set(base+".crates."+id,stats.getInt(base+".crates."+id,0)+1);String label=reward.hasItemMeta()&&reward.getItemMeta().hasDisplayName()?ChatColor.stripColor(reward.getItemMeta().getDisplayName()):reward.getType().name();stats.set(base+".last-reward",label);List<String> recent=stats.getStringList(base+".recent");recent.add(0, id+":"+label);while(recent.size()>10)recent.remove(recent.size()-1);stats.set(base+".recent",recent);try{stats.save(statsFile);}catch(java.io.IOException ex){getLogger().warning("Nie można zapisać stats.yml: "+ex.getMessage());}
        double chance=rewardChance(reward); if(chance<0) chance=100.0/Math.max(1,rewards(id).size()); double threshold=getConfig().getDouble("settings.rare-broadcast-below-percent",1.0);if(getConfig().getBoolean("settings.rare-broadcast",true)&&chance<threshold){String announcement=color(getConfig().getString("settings.rare-broadcast-message","&d✦ &f%player% &7wylosował rzadką nagrodę &e%reward% &7ze skrzynki &6%crate%" ).replace("%player%",p.getName()).replace("%reward%",label).replace("%crate%",getConfig().getString(path(id,"display-name"),id)));Bukkit.broadcastMessage(announcement);}}
    public int getOpened(UUID uuid){return stats==null?0:stats.getInt("players."+uuid+".opened",0);}
    public int getOpened(UUID uuid,String crate){return stats==null?0:stats.getInt("players."+uuid+".crates."+crate,0);}
    public String getLastReward(UUID uuid){return stats==null?"brak":stats.getString("players."+uuid+".last-reward","brak");}
    public List<String> getRecent(UUID uuid){return stats==null?List.of():stats.getStringList("players."+uuid+".recent");}
    private void removeHolograms(String id){for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e instanceof ArmorStand as&&as.getScoreboardTags().contains("skrzy_holo_"+id))as.remove();}
    private void refreshHolograms(){for(String id:crateIds()){removeHolograms(id);String wn=getConfig().getString(path(id,"location.world"));if(wn==null)continue;World w=Bukkit.getWorld(wn);if(w==null)continue;double x=getConfig().getDouble(path(id,"location.x"))+.5,y=getConfig().getDouble(path(id,"location.y"))+1.3,z=getConfig().getDouble(path(id,"location.z"))+.5;List<String> lines=getConfig().getStringList(path(id,"hologram"));if(lines.isEmpty())lines=List.of("&6&l"+getConfig().getString(path(id,"display-name"),id),"&eKliknij, aby otworzyć");for(int i=lines.size()-1;i>=0;i--){String line=lines.get(i);w.spawn(new Location(w,x,y+(lines.size()-1-i)*.25,z),ArmorStand.class,as->{as.setVisible(false);as.setGravity(false);as.setMarker(true);as.setCustomName(color(line));as.setCustomNameVisible(true);as.setSilent(true);as.setInvulnerable(true);as.addScoreboardTag("skrzy_holo_"+id);});}}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a){if(a.length==1)return List.of("help","list","create","get","edit","setlocation","removelocation","delete","give","giveall","chance","reload","stats").stream().filter(x->x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();if(a.length==2&&List.of("get","edit","delete","setlocation","removelocation","giveall").contains(a[0].toLowerCase(Locale.ROOT)))return crateIds().stream().filter(x->x.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();return List.of();}
}
