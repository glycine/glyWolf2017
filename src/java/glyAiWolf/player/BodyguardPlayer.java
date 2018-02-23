package glyAiWolf.player;

import java.util.ArrayList;
import java.util.List;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;

/**
 * 狩人用のクラス．護衛のみ実装．残りは村人と共通．
 * 
 * TODO: CO関係の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class BodyguardPlayer extends BasePlayer {
	@Override
	public void dayStart() {
		super.dayStart();
		// 1日目に村人とCOする
		if (this.latestGameInfo.getDay() == 1) {
			Content content = new Content(new ComingoutContentBuilder(this.latestGameInfo.getAgent(), Role.VILLAGER));
			this.myDeclare.addLast(content);
		}
	}

	/**
	 * 護衛対象を決定する．
	 * ロジックとしては，生きている人の中で，占い師らしい人->霊媒師らしい人->村人らしい人の順序で護衛する
	 * 
	 */
	@Override
	public Agent guard() {
		List<Agent> probSeers = new ArrayList<>();
		List<Agent> probMediums = new ArrayList<>();
		List<Agent> probVillagers = new ArrayList<>();
		for (Agent agent : this.latestGameInfo.getAliveAgentList()) {
			// 自分自身はスキップ
			if (agent.equals(this.latestGameInfo.getAgent())) {
				continue;
			}
			Role assumedRole = assumeRole(agent);
			switch (assumedRole) {
			case SEER:
				probSeers.add(agent);
				break;
			case MEDIUM:
				probMediums.add(agent);
				break;
			case VILLAGER:
				probVillagers.add(agent);
				break;
			default:
			}
		}
		if (!probSeers.isEmpty()) {
			return choiceAgent(probSeers);
		} else if (!probMediums.isEmpty()) {
			return choiceAgent(probMediums);
		} else if (!probVillagers.isEmpty()) {
			return choiceAgent(probVillagers);
		} else {
			return choiceAgent(this.latestGameInfo.getAliveAgentList());
		}
	}
}
