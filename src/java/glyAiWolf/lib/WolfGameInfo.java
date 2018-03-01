package glyAiWolf.lib;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.Content;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * Whisperによって得られる情報を管理するGameInfo
 * 
 * @author "Haruhisa Ishida<haruhisa.ishida@gmail.com>"
 *
 */
public class WolfGameInfo extends BaseGameInfo {
	// 狼のattack宣言．[agentIndex]で引く
	public Agent[] attackStatus;
	// ゲーム全体での狼集合
	public Set<Agent> werewolfs;
	// ゲーム全体での生きている狼集合
	public Set<Agent> aliveWerewolfs;
	// このゲームで観測されたwhispter
	public Deque<Talk> whispers;

	public WolfGameInfo(GameInfo gameInfo, GameSetting gameSetting) {
		super(gameInfo, gameSetting);
		this.whispers = new ConcurrentLinkedDeque<>();
		this.attackStatus = new Agent[gameSetting.getPlayerNum()];
		Arrays.fill(this.attackStatus, null);
		this.werewolfs = new HashSet<>();
		this.aliveWerewolfs = new HashSet<>();
	}

	/**
	 * 日付変更によってリセットすべき変数を処理する
	 */
	@Override
	public void dayChange() {
		super.dayChange();
		Arrays.fill(this.attackStatus, null);
	}

	/**
	 * 襲撃対象の
	 * 
	 * @return
	 */
	public Map<Agent, Integer> getAttackStatus() {
		Map<Agent, Integer> result = new HashMap<>();
		for (Agent target : this.attackStatus) {
			if (target != null) {
				if (!result.containsKey(target)) {
					result.put(target, 1);
				} else {
					result.put(target, result.get(target) + 1);
				}
			}
		}
		return result;
	}

	public Set<Agent> getAliveVillagers() {
		Set<Agent> result = new HashSet<>();
		for (Agent agent : this.latestGameInfo.getAliveAgentList()) {
			if (!this.werewolfs.contains(agent)) {
				result.add(agent);
			}
		}
		return result;
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// whisper内容を反映する
		Deque<Talk> newWhispers = new ConcurrentLinkedDeque<>();
		for (Talk whisper : gameInfo.getWhisperList()) {
			if (this.whispers.isEmpty() || whisper.getIdx() > this.whispers.getLast().getIdx()) {
				newWhispers.addLast(whisper);
			}
		}
		while (!newWhispers.isEmpty()) {
			Talk whisper = newWhispers.pollFirst();
			this.handleWhisper(whisper);
		}
		// 生きている狼リストを更新する
		this.updateAliveWolfs(gameInfo.getAliveAgentList());
	}

	private void updateAliveWolfs(List<Agent> aliveAgents) {
		this.aliveWerewolfs.clear();
		for (Agent agent : aliveAgents) {
			if (this.werewolfs.contains(agent)) {
				this.aliveWerewolfs.add(agent);
			}
		}
	}

	private void handleWhisper(Talk whisper) {
		Agent agent = whisper.getAgent();
		// wolfリストに追加
		this.werewolfs.add(agent);
		Content content = new Content(whisper.getText());
		switch (content.getTopic()) {
		case AGREE:
			break;
		case ATTACK:
			break;
		case COMINGOUT:
			break;
		case DISAGREE:
			break;
		case DIVINATION:
			break;
		case DIVINED:
			break;
		case ESTIMATE:
			break;
		case GUARD:
			break;
		case GUARDED:
			break;
		case IDENTIFIED:
			break;
		case OPERATOR:
			break;
		case OVER:
			break;
		case SKIP:
			break;
		case VOTE:
			break;
		default:
			break;
		}
	}

}
