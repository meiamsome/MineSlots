package me.meiamsome.mineslots;


import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

public class Machine implements Runnable {
	MineSlots ms;
	private double baseJack, jackpot;
	int sides;
	char[] reels;
	private int time=0;
	private Sign sign;
	private int frozen =0;
	private OfflinePlayer owner,player;
	private double stake;
	private Random rand=new Random();
	private int recurJack;
	private int colOff;
	boolean xrys;
	private Boolean didWin = null;
	Machine(MineSlots m, double st, double jack, int r, int s, int rj, boolean isXRYS) {
		ms=m;
		stake = st;
		baseJack=jackpot=jack;
		sides=Math.min(26,s);
		reels = new char[r];
		recurJack=rj;
		xrys = isXRYS;
	}
	public void setSettings(Sign s, Player p) {
		sign=s;
		player=p;
		if(sign!=null && ms.curJack.containsKey(sign.getBlock().getLocation())) jackpot=ms.curJack.get(sign.getBlock().getLocation());
	}
	public boolean isRecursive() {
		return recurJack!=0;
	}
	public double getChance() {
		return Math.pow(sides, reels.length-1);
	}
	public int getRecursive() {
		return recurJack;
	}
	public boolean isXRYS() {
		return xrys;
	}
	public double getJackpot() {
		return jackpot;
	}
	public double getStake() {
		return stake;
	}
	public double getBaseJackpot() {
		return baseJack;
	}
	public Boolean hasWon() {
		return didWin;
	}
	public void roundJackpot(double round) {
		if(jackpot == baseJack) {
			jackpot = Math.floor(jackpot/round)*round;
			baseJack = jackpot;
		}
	}
	public void setJackpot(double jack) {
		baseJack = jackpot = jack;
	}
	public void start() {
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(ms, this, 1);	
	}
	@Override
	public void run() {
		if(frozen==reels.length+10) {
			if(sign!=null) ms.machines.remove(sign.getBlock().getLocation());
			return;
		}
		if(frozen>=reels.length) {
			frozen++;
			start();
			return;
		}
		if(time==0) {
			if(player!=null) if(!ms.econ.has(player.getName(), stake)) {
				if(player.isOnline()) ((Player)player).sendMessage(ChatColor.RED+"You do not have enough money to use this machine!");
				return;
			}
			if(player!=null) ms.econ.withdrawPlayer(player.getName(), stake);
			if(sign == null || sign.getLine(1).length()==0) {
				owner=null;
			} else {
				ms.econ.depositPlayer(sign.getLine(1), stake);
				if(!ms.econ.has(sign.getLine(1), jackpot)) {
					if(player.isOnline()) ((Player)player).sendMessage(ChatColor.RED+"Sign creator does not have enough money!");
					ms.econ.withdrawPlayer(sign.getLine(1), stake);
					ms.econ.withdrawPlayer(player.getName(), stake);
					return;
				}
				owner = ms.getServer().getOfflinePlayer(sign.getLine(1));
				ms.econ.withdrawPlayer(sign.getLine(1), jackpot);
			}
			if(sign!=null) ms.machines.put(sign.getBlock().getLocation(), this);
			if(player!=null && player.isOnline()) ((Player)player).sendMessage(ChatColor.AQUA+"You pulled the lever! ("+ChatColor.RED+"-"+ms.econ.format(stake)+ChatColor.AQUA+")");
			colOff = rand.nextInt()%16;
			if(colOff<0) colOff=-colOff;
		}
		time++;
		for(int i=frozen;i<reels.length; i++) {
			reels[i]=(char) ('A'+rand.nextInt(sides));
		}
		if(time>10) {
			if(rand.nextInt(10)==1) {
				if(ms.conf.getConfigurationSection("global").getBoolean("progressive")) {
					frozen++;
				} else frozen +=reels.length;
			}
		}
		String str="";
		for(char c:reels) {
			if(reels.length<=3 && (Boolean)ms.conf.get("global.useColours",true)) {
				int col=(int)(c-'A')+colOff;
				col %= 16;
				char colour;
				if(col<10) {
					colour=(char) ('0'+col);
				} else colour=(char) ('A'+col-10);
				str+="§"+colour+c+ChatColor.BLACK+"|";
			} else str+=c+"|";
		}
		str=str.substring(0, str.length()-1);
		if(reels.length<=3 && (Boolean)ms.conf.get("global.useColours",true)) str=str.substring(0, str.length()-2);
		if(sign!=null) {
			sign.setLine(3, str);
			sign.update();
		}
		start();
		if(frozen==reels.length){
			Boolean win=true;
			char cha = reels[0];
			for(char c:reels) if(c!=cha) win=false;
			if(player!=null)MetricsManager.self.addPlay();
			if(win) {
				if(player!=null) {
					MetricsManager.self.addWin(player.getName(), jackpot, stake);
					ms.econ.depositPlayer(player.getName(), jackpot);
					Integer highScore = ms.addHighScore(player.getName(), jackpot);
					if(jackpot>=ms.conf.getDouble("global.announce") && ms.conf.getDouble("global.announce")>=0) {
						if(highScore==null) {
							ms.getServer().broadcastMessage(ChatColor.BLUE+"[MineSlots] "+player.getName()+" won "+ms.econ.format(jackpot)+" from a slot machine!");
						} else ms.getServer().broadcastMessage(ChatColor.BLUE+"[MineSlots] "+player.getName()+" won "+ms.econ.format(jackpot)+" from a slot machine, taking position "+highScore+" on the high scores!");
					} else {
						if(player.isOnline()) if(highScore==null) {
							((Player)player).sendMessage(ChatColor.GREEN+"You won "+ms.econ.format(jackpot)+"!");
						} else ((Player)player).sendMessage(ChatColor.GREEN+"You won "+ms.econ.format(jackpot)+", taking position "+highScore+" on the high scores!");
					}
				}
				if(sign!=null) ms.curJack.remove(sign.getBlock().getLocation());
			} else {
				if(owner!=null) {
					if(isRecursive()) {
						ms.econ.depositPlayer(sign.getLine(1), jackpot);
					} else ms.econ.depositPlayer(sign.getLine(1), jackpot - Math.round(stake*recurJack)/100);
				}
				if(player!=null && player.isOnline()) ((Player)player).sendMessage(ChatColor.RED+"You lost.");
				if(sign!=null && isRecursive()) ms.curJack.put(sign.getBlock().getLocation(),(double)Math.round(100*jackpot+stake*recurJack)/100);
			}
			didWin = win;
		}
	}

	public static double calcEquilibriumJackpot(double reels, double sides, double stake, double recur) {
		return (Math.pow(sides, reels-1) * stake) * (100 - recur) / 100;
	}
	
}
