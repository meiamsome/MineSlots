package me.meiamsome.mineslots;

import java.io.IOException;
import java.util.HashMap;

import me.meiamsome.mineslots.Metrics.*;

public class MetricsManager {
	public static MetricsManager self;
	MineSlots plugin;
	boolean ok = false;
	Metrics met;
	HashMap<String, CustomPlotter> playerPlotters = new HashMap<String, CustomPlotter>(), playerByStakePlotters = new HashMap<String, CustomPlotter>();
	CustomPlotter totalWinningsPlot, totalPlaysPlot, winsOverStakePlot;
	Graph playerWins, totalWinnings, totalWinningsOverStake, playerWinsByStake;
	
	MetricsManager(MineSlots ms) {
		self = this;
		plugin = ms;
		try {
			met = new Metrics(plugin);
			playerWins = met.createGraph("Player Winnings");
			playerWinsByStake = met.createGraph("Player Winnings Divided by Stake");
			totalWinnings = met.createGraph("Total Winnings");
			totalWinningsPlot = new CustomPlotter("Total Winnings");
			totalWinnings.addPlotter(totalWinningsPlot);
			totalPlaysPlot = new CustomPlotter("Total Plays");
			totalWinnings.addPlotter(totalPlaysPlot);
			totalWinningsOverStake = met.createGraph("Total Winnings Divided by Stake");
			winsOverStakePlot = new CustomPlotter("Total Winnings Divided by Stake");
			totalWinningsOverStake.addPlotter(winsOverStakePlot);
			met.start();
			ok = true;
		} catch (IOException e) {
			System.out.println("Metrics IOException");
		}
	}
	public void addWin(String player, double amount, double stake) {
		if(!ok) return;
		totalWinningsPlot.data += (int)(amount*100);
		if(!playerPlotters.containsKey(player)) {
			CustomPlotter pl = new CustomPlotter(player);
			playerPlotters.put(player, pl);
			playerWins.addPlotter(pl);
		}
		playerPlotters.get(player).data += (int)(amount*100);
		//Over stake
		winsOverStakePlot.data += (int)(amount/stake*100);
		if(!playerByStakePlotters.containsKey(player)) {
			CustomPlotter pl = new CustomPlotter(player);
			playerByStakePlotters.put(player, pl);
			playerWinsByStake.addPlotter(pl);
		}
		playerByStakePlotters.get(player).data += (int)(amount/stake*100);
	}
	public void addPlay() {
		if(!ok) return;
		totalPlaysPlot.data ++;
	}
	public class CustomPlotter extends Metrics.Plotter {
		CustomPlotter(String name) {
			super(name);
		}
		int data = 0;
		@Override
		public int getValue() {
			int val = data;
			data = 0;
			return val;
		}
	}
}
