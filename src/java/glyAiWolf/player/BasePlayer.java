package glyAiWolf.player;

import java.util.Map;
import java.util.TreeMap;

import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class BasePlayer implements Player {
	protected Map<Integer, GameInfo> gameInfos = new TreeMap<>();
	protected GameSetting gameSetting;
	protected double[][] rolePossibility;

	@Override
	public Agent attack() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void dayStart() {
		// TODO 自動生成されたメソッド・スタブ

	}

	@Override
	public Agent divine() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void finish() {
		// TODO 自動生成されたメソッド・スタブ

	}

	/**
	 * 各playerの各役職に対する可能性の行列を作成する． <br/>
	 * 各行が各playerに対応し，各列が役職の可能性を表す
	 * 
	 * @param roleNumMap
	 * @return
	 */
	protected double[][] genRolePossibility(GameSetting gameSetting) {
		double[][] rolePossibility = new double[gameSetting.getPlayerNum()][Role.values().length];
		double base = 1.0 / (double) (gameSetting.getPlayerNum());
		for (int i = 0; i < gameSetting.getPlayerNum(); ++i) {
			for (Role role : Role.values()) {
				int roleNum = gameSetting.getRoleNum(role);
				rolePossibility[i][role.ordinal()] = base * (double) roleNum;
			}
		}
		return rolePossibility;
	}

	@Override
	public String getName() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public Agent guard() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	/**
	 * 配信されるgameInfoとgameSettingを保存
	 */
	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
		this.gameSetting = gameSetting.clone();
		this.rolePossibility = genRolePossibility(gameSetting);
		
		for (double[] row : rolePossibility) {
			for (double elem : row) {
				System.err.print(elem + ", ");
			}
			System.err.println("");
		}
	}

	@Override
	public String talk() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	/**
	 * 配信されるgameInfoを保存
	 */
	@Override
	public void update(GameInfo gameInfo) {
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
	}

	@Override
	public Agent vote() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public String whisper() {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

}
