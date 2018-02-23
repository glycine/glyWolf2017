package glyAiWolf.player;

import java.util.Arrays;
import java.util.List;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;

/**
 * 狂人用のクラス
 * とりあえず占い師を詐称し，占い結果を言い続ける
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class PossessedPlayer extends BasePlayer {
	/**
	 * 日の開始の処理
	 * 1日目以降： 占いCOの発話生成, 占い対象生成
	 */
	@Override
	public void dayStart() {
		super.dayStart();
		Agent me = this.latestGameInfo.getAgent();

		// 1日目に，偽の占いCOをする
		if (this.latestGameInfo.getDay() == 1) {
			Content content = new Content(new ComingoutContentBuilder(me, Role.SEER));
			this.myDeclare.add(content);
		}
		// 1日目以降は，偽の占い結果を出し続ける
		if (this.latestGameInfo.getDay() >= 1) {
			// 占い対象候補: 生きている人のうち、自分以外で、占っていない人。対象者がいない場合は
			List<Agent> agents = Arrays.asList(
					this.latestGameInfo.getAliveAgentList().stream().filter(x -> x.getAgentIdx() != me.getAgentIdx())
							.filter(y -> this.talkMatrix[me.getAgentIdx() - 1][y.getAgentIdx() - 1][Topic.DIVINED
									.ordinal()] == 0)
							.toArray(Agent[]::new));
			Agent divinedAgent = choiceAgent(agents);
			if (divinedAgent == null) {
				divinedAgent = me;
			}
			Content divinedContent = new Content(new DivinedResultContentBuilder(divinedAgent, Species.HUMAN));
			this.myDeclare.addLast(divinedContent);
			// 占った記録をつける
			this.talkMatrix[me.getAgentIdx() - 1][divinedAgent.getAgentIdx() - 1][Topic.DIVINED.ordinal()]++;
		}
	}

	/**
	 * 狂人用の予想発話メソッド
	 * 自分が占いといい，あと狼を推測して発話する
	 */
	@Override
	protected void estimateRoleMap() {
		Agent me = this.latestGameInfo.getAgent();
		this.myDeclare.addLast(new Content(new EstimateContentBuilder(this.latestGameInfo.getAgent(), Role.SEER)));
		List<Agent> targets = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
				.filter(x -> x.getAgentIdx() != me.getAgentIdx()).toArray(Agent[]::new));
		List<Role> assumedRoles = Arrays.asList(targets.stream().map(x -> this.assumeRole(x)).toArray(Role[]::new));
		for (int i = 0; i < targets.size(); ++i) {
			Role assumedRole = assumedRoles.get(i);
			if (assumedRole.equals(Role.WEREWOLF)) {
				this.myDeclare.addLast(new Content(new EstimateContentBuilder(targets.get(i), assumedRole)));
			}
		}
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);

	}

	/**
	 * 投票の決定
	 * 自分以外
	 * 生きている
	 * 最も占い師らしい人
	 */
	@Override
	public Agent vote() {
		Agent me = this.latestGameInfo.getAgent();
		List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
				.filter(x -> x.getAgentIdx() != me.getAgentIdx()).toArray(Agent[]::new));
		Agent target = me;
		double prob = -1.0;
		for (Agent agent : agents) {
			if (prob < this.rolePossibility[agent.getAgentIdx() - 1][Role.SEER.ordinal()]) {
				prob = this.rolePossibility[agent.getAgentIdx() - 1][Role.SEER.ordinal()];
				target = agent;
			}
		}
		return target;
	}
}
