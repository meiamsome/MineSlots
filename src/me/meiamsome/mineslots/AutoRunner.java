package me.meiamsome.mineslots;

import org.bukkit.entity.Player;

public class AutoRunner implements Runnable {
	int totalTimes, times, scheduleID, wins, loss;
	double stake, money = 0, curRec = 0;
	MineSlots ms;
	Player play;
	Machine[] machs;
	String name;
	AutoRunner(MineSlots mns, int time, Player pl, String nme, double stke) throws Exception {
		ms = mns;
		totalTimes = times = time;
		play = pl;
		name = nme;
		stake = stke;
		play.sendMessage("Running "+times+" times");
		if(times>1000) {
			machs = new Machine[1000];
		} else machs = new Machine[times];
		for(int i=0; i < machs.length; i++) {
			machs[i] = ms.getMachine(name, stake, null);
			machs[i].start();
			times--;
			money -=stake;
		}
	}

	@Override
	public void run() {
		if(wins + loss == totalTimes) {
			ms.getServer().getScheduler().cancelTask(scheduleID);
			play.sendMessage("Task run. "+totalTimes+" machines ran, "+wins+" wins "+loss+" losses. ("+((double)wins/totalTimes)+")");
			play.sendMessage("Money: "+ms.econ.format(money)+" Left in Jackpot: "+ms.econ.format(curRec));
			try {
				double chance = 1/ms.getMachine(name, stake, null).getChance();
				play.sendMessage("Expected probability: " + chance);
			} catch (Exception e) {
				play.sendMessage("Erm?");
			}
			return;
		}
		Machine mach;
		for(int i=0; i< machs.length; i++) {
			mach = machs[i];
			if(mach == null) continue;
			if(mach.hasWon() != null) {
				if(mach.hasWon()) {
					wins++;
					money+=mach.getJackpot() + curRec;
					curRec=0;
				} else loss++;
				if(mach.isRecursive()) {
					curRec += stake * mach.getRecursive()/100;
				}
				if(times == 0) {
					machs[i] = null;
				} else {
					try {
						machs[i] = ms.getMachine(name, stake, null);
					} catch(Exception e) {
						play.sendMessage("Errrrm");
						return;
					}
					machs[i].start();
					times--;
					money-=stake;
				}
				if((wins + loss) %100 == 0) {
					play.sendMessage("Completed "+(wins+loss)+" trials");
				}
			}
		}
	}
	public void setSyncID(int id) {
		scheduleID = id;
	}

}
