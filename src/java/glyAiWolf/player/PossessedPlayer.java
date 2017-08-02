package glyAiWolf.player;

import java.util.Arrays;
import java.util.List;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
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
		// 0日目と1日目以降で処理を分ける
		if (this.latestGameInfo.getDay() > 0) {
			Content content = new Content(new ComingoutContentBuilder(this.latestGameInfo.getAgent(), Role.SEER));
			this.myTalks.add(content);

			Agent me = this.latestGameInfo.getAgent();

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
			this.myTalks.addLast(divinedContent);
			// 占った記録をつける
			this.talkMatrix[me.getAgentIdx() - 1][divinedAgent.getAgentIdx() - 1][Topic.DIVINED.ordinal()]++;
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