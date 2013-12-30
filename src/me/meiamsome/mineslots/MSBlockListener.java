package me.meiamsome.mineslots;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.material.Attachable;

public class MSBlockListener implements Listener {
	MineSlots ms;
	public MSBlockListener(MineSlots MS) {
		ms=MS;
	}
	@EventHandler
	public void onBlockDamage(BlockDamageEvent event){
		if(event.isCancelled()) return;
		if(event.getBlock().getState() instanceof Sign) {
			event.setCancelled(ms.signClick(event.getPlayer(), (Sign)event.getBlock().getState(), Action.LEFT_CLICK_BLOCK));
		}
	}
	public HashSet<Sign> getAttachedSigns(Block b, BlockFace bf) {
		HashSet<Sign> signs = new HashSet<Sign>();
		for(BlockFace face: new BlockFace[]{BlockFace.WEST,BlockFace.EAST,BlockFace.SOUTH,BlockFace.NORTH, BlockFace.SELF, BlockFace.UP}) {
			if(bf == null || face != bf.getOppositeFace()) {
				if(face == BlockFace.SELF) {
					if(b.getState() instanceof Sign) signs.add((Sign)b.getState());
				} else {
					BlockState otherBS = b.getRelative(face).getState();
					if(shouldTrack(otherBS, face)){
						HashSet<Sign> search = getAttachedSigns(b.getRelative(face), face);
						for(Sign s: search) signs.add(s);
					}
				}
			}
		}
		return signs;
	}
	public boolean shouldTrack(BlockState bs, BlockFace face) {
		if(bs.getData() instanceof Attachable) {
			return ((Attachable)bs.getData()).getAttachedFace() == face.getOppositeFace();
		}
		if(face == BlockFace.UP && (bs.getTypeId() == 12 || bs.getTypeId() == 13)) return true;
		return false;
	}
	public void canBreak(Block b, Player p) throws Exception {
		if(p!=null && p.hasPermission("MS.deleteAny")) return;
		Exception e = null;
		HashSet<Sign> signs = getAttachedSigns(b, null);
		for(Sign sign: signs) {
			if(e == null && sign.getLine(0).toLowerCase().startsWith("[slots") && sign.getLine(0).endsWith("]")) {
				if(p == null || !sign.getLine(1).equals(p.getName().substring(0, Math.min(15, p.getName().length())))) e = new Exception("You can't destroy "+(sign.getLine(1).isEmpty() ? "Server":(sign.getLine(1)+"'s" ))+" signs.");
				if(e == null && ms.machines.containsKey(sign.getBlock().getLocation())) e = new Exception("A connected slot machine is in use.");
			}
			sign.update();
		}
		if(e != null) throw e;
	}
	public void handleBreak(Block b) {
		HashSet<Sign> signs = getAttachedSigns(b, null);
		for(Sign sign: signs) {
			if(ms.curJack.containsKey(sign.getBlock().getLocation())) {
				ms.curJack.remove(sign.getBlock().getLocation());
			}
			try {
				if(ms.getMachine(sign) != null)
					sign.getBlock().breakNaturally();
			} catch(Exception e) {};
		}
	}
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		try {
			canBreak(event.getBlock(), event.getPlayer());
		} catch(Exception e) {
			event.getPlayer().sendMessage(ChatColor.RED + e.getMessage());
			event.setCancelled(true);
		}
	}
	@EventHandler (priority=EventPriority.MONITOR)
	public void onBlockBreakMonitor(BlockBreakEvent event) {
		if(!event.isCancelled()) handleBreak(event.getBlock());
	}
/*
	@EventHandler
	public void onGravity(BlockPhysicsEvent event) {
		try {
			canBreak(event.getBlock(), null);
		} catch(Exception e) {
			event.setCancelled(true);
		}
	}
	@EventHandler (priority=EventPriority.MONITOR)
	public void onGravityMonitor(BlockPhysicsEvent event) {
		if(!event.isCancelled()) handleBreak(event.getBlock());
	}*/
	@EventHandler
	public void onPistonExtend(BlockPistonExtendEvent event) {
		try {
			for(Block b: event.getBlocks()) canBreak(b, null);
			if(event.isSticky())
				canBreak(event.getBlock().getRelative(event.getDirection(), 2), null);
		} catch(Exception e) {
			event.setCancelled(true);
		}
	}
	@EventHandler
	public void onBlockBurn(BlockBurnEvent event) {
		try {
			canBreak(event.getBlock(), null);
		} catch(Exception e) {
			event.setCancelled(true);
		}
	}
	@EventHandler
	public void onExplosion(EntityExplodeEvent event) {
		Iterator<Block> iter = event.blockList().iterator();
		while(iter.hasNext())
			try {
				canBreak(iter.next(), null);
			} catch(Exception e) {
				iter.remove();
			}
	}
	
	@EventHandler (priority = EventPriority.HIGHEST)
	public void onSignChange(SignChangeEvent event) {
		if(event.getLine(0).toLowerCase().startsWith("[slots") && event.getLine(0).endsWith("]")) {
			if(!event.getPlayer().hasPermission("MS.create")) {
				event.setCancelled(true);
				event.setLine(0, "[?]");
				event.getBlock().breakNaturally();
				event.getPlayer().sendMessage(ChatColor.RED+"You don't have the permission to do that!");
				return;
			}
			if(!event.getPlayer().hasPermission("MS.createAny")) event.setLine(1, event.getPlayer().getName());
			if(ms.curJack.containsKey(event.getBlock().getLocation())) ms.curJack.remove(event.getBlock().getLocation());
			String name = event.getLine(0).substring(event.getLine(0).toLowerCase().startsWith("[slots ")?7:6);
			name = name.substring(0, name.length()-1);
			Machine mach;
			try {
				 mach = ms.getMachine(name, 1, null);
			} catch(Exception e) {
				event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, sign is invalid:");
				event.getPlayer().sendMessage(ChatColor.RED+e.getMessage());
				event.getBlock().breakNaturally();
				return;
			}
			if(mach.isXRYS()) {
				if(mach.getRecursive()>ms.conf.getInt("global.XRYS.maxJackAdd")) {
					event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, stake reinvestment cannot exceed "+ms.conf.getInt("global.XRYS.maxJackAdd")+"%!");
					event.getBlock().breakNaturally();
					return;
				}
			}
			if(mach.getRecursive()<0) {
				event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, stake reinvestment cannot be negative!");
				event.getBlock().breakNaturally();
				return;
			}
			String[] StakeJack= event.getLine(2).split(":");
			double stake;
			Double jack = null;
			try {
				stake=Double.parseDouble(StakeJack[0]);
				if(stake < 0) {
					event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, stake must be positive!");
					event.getBlock().breakNaturally();
					return;
				}
				double round = (Double)ms.conf.get("global.stakeRound", 0.01);
				if(stake/round != Math.round(stake/round)) {
					stake = (double)Math.round(stake/round)*round;
					event.getPlayer().sendMessage(ChatColor.RED+"Stake has been rounded to the nearest "+round+"!");
				}
				try {
					if(Double.parseDouble((new DecimalFormat("#.##")).format(stake))!=stake) {
						event.getPlayer().sendMessage(ChatColor.RED+"Stake has been rounded to two decimal places!");
						stake = Double.parseDouble((new DecimalFormat("#.##")).format(stake));
					}
				} catch (Exception e) {
					event.getPlayer().sendMessage(ChatColor.RED+"An unknown error occurred!");
					event.getBlock().breakNaturally();
				}
				
			} catch(NumberFormatException e) {
				double round = (Double)ms.conf.get("global.stakeRound", 0.01);
				stake=Math.round((Double)ms.conf.get("global.stakeDefault", 1.0)/round)*round;;
				event.getPlayer().sendMessage(ChatColor.RED+"Stake defaulted to "+stake+"!");
			}
			if(StakeJack.length >= 2) {
				try {
					jack = Double.parseDouble(StakeJack[1]);
				} catch(NumberFormatException e) {
					event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, jackpot could not be intepreted!");
					event.getBlock().breakNaturally();
					return;
				}
				if(jack < 0) {
					event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, jackpot must be positive!");
					event.getBlock().breakNaturally();
					return;
				}
			}
			mach.setJackpot(mach.getBaseJackpot()*stake);
			if(mach.isXRYS())
				if(mach.getBaseJackpot()>ms.conf.getInt("global.XRYS.round")) mach.roundJackpot(ms.conf.getInt("global.XRYS.round"));
			if(jack != null && jack != mach.getBaseJackpot()) {
				if(!mach.isXRYS()) {
					if(!ms.conf.getBoolean("machines."+name+".allowCustomJackpot", false)) {
						event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, jackpot cannot be customised for this machine!");
						event.getBlock().breakNaturally();
						return;
					}
				} else if(!ms.conf.getBoolean("global.XRYS.allowCustomJackpot", false)) {
					event.getPlayer().sendMessage(ChatColor.RED+"Creation failed, jackpot cannot be custom!");
					event.getBlock().breakNaturally();
					return;
				}
			}
			event.setLine(2, (new DecimalFormat("#.##")).format(stake)+(jack == null? "" :(":"+(new DecimalFormat("#.##")).format(jack))));
			event.getBlock().getState().update();
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.isCancelled()) return;
		if(!event.hasBlock()) return;
		Block b=event.getClickedBlock();
		if(b.getState() instanceof Sign) {
			Sign sign = (Sign)b.getState();
			event.setCancelled(ms.signClick(event.getPlayer(), sign, event.getAction()));
		}
	}
}
