package me.meiamsome.mineslots;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MineSlots extends JavaPlugin {
	Economy econ = null;
	PluginManager pm;
	MSBlockListener Listener = new MSBlockListener(this);
	HashMap<Location, Machine> machines = new HashMap<Location, Machine>();
	HashMap<Location, Double> curJack = new HashMap<Location, Double>();
	double[] highScores;
	String[] highScoreNames;
	
	FileConfiguration conf;
	@Override
	public void onEnable() {
		pm=getServer().getPluginManager();
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }
		pm.registerEvents(Listener, this);
		saveDefaultConfig();
		conf = getConfig();
		try{
			double val = (Double)conf.get("global.stakeRound");
			if(Math.round(val*100)!=val*100) getLogger().warning("Rounding to more than two decimal places may cause instability.");
		} catch(Exception e) {}
		//Load Old Jackpots in
		int loadErr = 0;
		ArrayList<String> errors = new ArrayList<String>();
		FileConfiguration fc = new YamlConfiguration();
		try {
			fc.load(getDataFolder()+System.getProperty("file.separator")+"curpots.yml");
			Set<String> worlds = fc.getConfigurationSection("jackpots").getKeys(false);
			for(String world:worlds) {
				World w = getServer().getWorld(world);
				if(w==null) {
					int failed = fc.getConfigurationSection("jackpots."+world).getKeys(false).size();
					errors.add("\tFailed to load "+failed+" jackpot"+(failed>1?"s":"")+" on world `"+world+"`, due to the world no longer existing.");
					loadErr += failed;
					continue;
				}
				Set<String> locations = fc.getConfigurationSection("jackpots."+world).getKeys(false);
				for(String loc:locations) {
					try{
						String[] ls = loc.split(",");
						if(ls.length!=3) continue;
						curJack.put(new Location(w,Integer.parseInt(ls[0]),Integer.parseInt(ls[1]),Integer.parseInt(ls[2])), fc.getDouble("jackpots."+world+"."+loc));
					} catch(Exception e) {loadErr++;}
				}
			}
		} catch (Exception e) {}
		if(loadErr!=0) {
			getLogger().warning("Failed to load "+loadErr+" jackpot"+(loadErr>1?"s":"")+(errors.size()>0?":":""));
			for(String err: errors) getLogger().warning(err);
		}
		
		new MetricsManager(this);
		//Load in high scores
		highScores = new double[conf.getInt("global.storedHighScores")];
		for(int i = 0;  i < highScores.length; i++) highScores[i]=-1;
		highScoreNames = new String[highScores.length];
		FileConfiguration highsc = new YamlConfiguration();
		try {
			highsc.load(getDataFolder()+System.getProperty("file.separator")+"highscores.yml");
			for(int i = 0; i < highScores.length; i++) {
				if(!highsc.contains("highscores."+i)) continue;
				highScores[i] = highsc.getDouble("highscores."+i+".money");
				highScoreNames[i] = highsc.getString("highscores."+i+".name");
			}
		} catch (Exception e) {
			
		}
		
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable(){public void run() {
			saveJackpots();
		}}, 1200 * conf.getInt("global.saveInterval",15), 1200 * conf.getInt("global.saveInterval",15));
	}
	@Override
	public void onDisable() {
		saveJackpots();
	}
	public void saveJackpots() {
		FileConfiguration fc = new YamlConfiguration();
		for(Location l: curJack.keySet()) fc.set("jackpots."+l.getWorld().getName()+"."+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ(),curJack.get(l));
		try {
			fc.save(getDataFolder()+System.getProperty("file.separator")+"curpots.yml");
			getLogger().info("Saved current jackpots");
		} catch (IOException e) {
			getLogger().severe("Failed to save current jackpots");
		}
	}
	public void saveHighScores() {
		FileConfiguration highsc = new YamlConfiguration();
		for(int i = 0; i < highScores.length; i++){
			if(highScores[i] == -1) continue;
			highsc.set("highscores."+i+".money", highScores[i]);
			highsc.set("highscores."+i+".name", highScoreNames[i]);
		}
		try {
			highsc.save(getDataFolder()+System.getProperty("file.separator")+"highscores.yml");
			getLogger().info("Saved current highscores");
		} catch (IOException e) {
			getLogger().severe("Failed to save current highscores");
		}
	}
	public Integer addHighScore(String name, double score) {
		for(int i = 0; i < highScores.length; i++) {
			if(highScores[i] < score) {
				for(int j = highScores.length-1; j > i; j--) {
					highScores[j] = highScores[j-1];
					highScoreNames[j] = highScoreNames[j-1];
				}
				highScores[i] = score;
				highScoreNames[i] = name;
				saveHighScores();
				return i+1;
			}
		}
		return null;
	}
	public Machine getMachine(Sign sign) throws Exception {
		if(!(sign.getLine(0).toLowerCase().startsWith("[slots") && sign.getLine(0).endsWith("]"))) return null;
		String type = sign.getLine(0).substring(sign.getLine(0).toLowerCase().startsWith("[slots ")?7:6);
		if(type.length()>0)type=type.substring(0, type.length()-1);
		String[] StakeJack= sign.getLine(2).split(":");
		double stake;
		Double jack = null;
		try {
			stake = Double.parseDouble(StakeJack[0]);
		} catch(NumberFormatException e) {
			stake=1;
		}
		try {
			if(StakeJack.length >= 2) jack = Double.parseDouble(StakeJack[1]);
		} catch(NumberFormatException e) {
			jack = null;
		}
		return getMachine(type, stake, jack);
	}
	public Machine getMachine(String name, double stake, Double signJack) throws Exception {
		if(name.trim().length()==0) name = conf.getString("global.defaultMachine");
		int reels, sides, recurJack;
		double jack;
		ConfigurationSection mach = conf.getConfigurationSection("machines");
		Machine m;
		if(mach.isConfigurationSection(name)) {
			mach=mach.getConfigurationSection(name);
			reels=mach.getInt("reels",3);
			sides=mach.getInt("sides",4);
			recurJack=mach.getInt("jackAdd",0);
			jack= stake * mach.getDouble("jackpot", Machine.calcEquilibriumJackpot(reels, sides, 1, recurJack)*conf.getInt("global.XRYS.percent")/100);
			if(signJack != null) if(mach.getBoolean("allowCustomJackpot", false)) {
				jack = signJack;
			} else if(jack != signJack) throw new Exception("Custom jackpot amounts are not allowed on these signs.");
			m = new Machine(this, stake, jack, reels, sides, recurJack, false);
		} else if(conf.getBoolean("global.XRYS.allow") && name.toLowerCase().matches("[0-9]*r[0-9]*s([+][0-9]*)?")) {
			reels=Integer.parseInt(name.substring(0, name.toLowerCase().indexOf("r")));
			sides=Integer.parseInt(name.substring(name.toLowerCase().indexOf("r")+1, name.toLowerCase().indexOf("s")));
			if(reels<2) throw new Exception("Signs cannot have less than 2 reels.");
			if(sides<2) throw new Exception("Signs cannot have less than 2 sides.");
			if(name.toLowerCase().matches("[0-9]*r[0-9]*s[+][0-9]*")) {
				recurJack=conf.getInt("global.XRYS.jackAdd",0);
				if(!name.endsWith("+")) {
					recurJack=Integer.parseInt(name.substring(name.toLowerCase().indexOf("+")+1,name.length()));
				}
			} else recurJack=0;
			jack=Machine.calcEquilibriumJackpot(reels, sides, stake, recurJack)*conf.getInt("global.XRYS.percent")/100;
			if(recurJack > conf.getInt("global.XRYS.maxJackAdd")) throw new Exception("Jackpot reinvestment cannot exceed " + conf.getInt("global.XRYS.maxJackAdd"));
			m = new Machine(this, stake, jack, reels, sides, recurJack, true);
			if(m.getBaseJackpot()>conf.getDouble("global.XRYS.round")) m.roundJackpot(conf.getDouble("global.XRYS.round"));
			if(signJack != null) if(conf.getBoolean("global.XRYS.allowCustomJackpot", false)) {
				m.setJackpot(signJack);
			} else if(m.getBaseJackpot() != signJack)  throw new Exception("Custom jackpot amounts are not allowed on these signs.");
		} else {
			throw new Exception("Unknown sign type.");
		}
		if(m.isRecursive() && m.getRecursive() < 0) throw new Exception("Jackpot reinvestment cannot be negative!");
		return m;
	}
	public boolean signClick(Player player, Sign sign, Action action) {
		Machine machine;
		try {
			machine=getMachine(sign);
			if(machine == null) return false;
		} catch(Exception e) {
			player.sendMessage(ChatColor.RED+"Error with sign:");
			player.sendMessage(ChatColor.RED+e.getMessage());
			return false;
		}
		machine.setSettings(sign, player);
		if(action==Action.RIGHT_CLICK_BLOCK) {
			if(machines.containsKey(sign.getBlock().getLocation())) {
				player.sendMessage(ChatColor.RED+"This machine is in use!");
			} else machine.start();
			return true;
		} else {
			player.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~ MineSlots ~~~~~~~~~~~~~~~~~~~");
			player.sendMessage(ChatColor.GREEN+"Stake: "+ChatColor.WHITE+econ.format(machine.getStake()));
			player.sendMessage(ChatColor.GREEN+"Jackpot: "+ChatColor.WHITE+econ.format(machine.getBaseJackpot())+
					(machine.isRecursive()?ChatColor.GREEN+"  ("+ChatColor.BLUE+"+"+econ.format(machine.getJackpot()-machine.getBaseJackpot())+ChatColor.GREEN+")":""));
			player.sendMessage(ChatColor.GREEN+"Reels: "+ChatColor.WHITE+machine.reels.length);
			player.sendMessage(ChatColor.GREEN+"Sides: "+ChatColor.WHITE+machine.sides);
			if(!sign.getLine(1).equals("")) {
				player.sendMessage(ChatColor.GREEN+"Owner: "+ChatColor.WHITE+sign.getLine(1));
				if(!econ.has(sign.getLine(1), machine.getJackpot()-machine.getStake())) 
					player.sendMessage(ChatColor.RED+"Sign creator does not have enough money!");
			}
			if(!econ.has(player.getName(), machine.getStake())) 
				player.sendMessage(ChatColor.RED+"You do not have enough money to use this machine!");
			if(machine.isRecursive()) player.sendMessage(ChatColor.GOLD+""+ChatColor.ITALIC+""+machine.getRecursive()+"% ("+econ.format(machine.getStake()*machine.getRecursive()/100)+")"+ChatColor.GREEN+" of the stake for this machine is added to the jackpot.");
			player.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
			return !(player.getName().substring(0, Math.min(15, player.getName().length())) == sign.getLine(1) || player.hasPermission("MS.deleteAny"));
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("Slots")) {
			/*if(!(sender instanceof Player)) {
				sender.sendMessage("Only players can use this!");
				return true;
			}*/
			if(args.length==0) {
				sender.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~ MineSlots ~~~~~~~~~~~~~~~~~~~");
				sender.sendMessage(ChatColor.GREEN+"Key: [] Required Argument, <> Optional Argument");
				sender.sendMessage(ChatColor.GREEN+"/slots top <page>");
				sender.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");				
				return true;
			} else if(args[0].equalsIgnoreCase("run")) {
				if(!sender.getName().equalsIgnoreCase("meiamsome")) {
					sender.sendMessage("Only meiamsome can use this!");
					return true;
				}
				int number;
				try {
					number = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					number = 100;
				}
				double stake;
				try {
					stake = Double.parseDouble(args[3]);
				} catch (NumberFormatException e) {
					stake = 1;
				}
				try {
					AutoRunner a=new AutoRunner(this, number, (Player)sender, args[2], stake);
					int x=getServer().getScheduler().scheduleSyncRepeatingTask(this, a, 20, 20);
					a.setSyncID(x);
				} catch(Exception e) {
					sender.sendMessage("Error with sign:");
					sender.sendMessage(e.getMessage());
				}
			} else if(args[0].equalsIgnoreCase("top")) {
				int page;
				try {
					if(args.length>=2) {
						page = Integer.parseInt(args[1]);
					} else page = 1;
				} catch (NumberFormatException e) {
					page = 1;
				}
				page--;
				if(page < 0) page = 0;
				int highNo = -1;
				for(int i = 0; i < highScores.length && highNo == -1; i++) {
					if(highScores[i]==-1) {
						highNo = i;
					}
				}
				if(highNo==-1) highNo = highScores.length;
				if(page*5 >= highNo) {
					page = (highNo - 1)/5;
				}
				sender.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~ MineSlots ~~~~~~~~~~~~~~~~~~~");
				sender.sendMessage(ChatColor.GREEN+"Top Scores page "+(page+1)+" of "+((highNo - 1)/5+1));
				for(int i = 0; i < 5; i++) if(highScores[page*5+i]!=-1) {
					ChatColor c;
					switch(page*5 + i) {
						case 0:
							c = ChatColor.GOLD;
							break;
						case 1:
							c = ChatColor.GRAY;
							break;
						case 2:
							c = ChatColor.DARK_RED;
							break;
						default:
							c = ChatColor.DARK_GREEN;
							break;
					}
					sender.sendMessage(c+""+(page*5+i+1)+") "+highScoreNames[page*5 + i]+": "+ChatColor.GREEN+econ.format(highScores[page*5+ i]));
				}
				sender.sendMessage(ChatColor.GREEN+"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");	
				
			} else {
				sender.sendMessage(ChatColor.RED + "[MineSlots] No command under that name. /slots for usage");
			}
			
			return true;
		}
		return false;
	}

}