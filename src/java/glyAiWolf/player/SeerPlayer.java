package glyAiWolf.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;

/**
 * 占い師の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class SeerPlayer extends BasePlayer {
	private Set<Judge> seerJudges = new HashSet<>();

	@Override
	public void dayStart() {
		super.dayStart();
		Agent me = this.latestGameInfo.getAgent();
		int myIndex = me.getAgentIdx() - 1;
		Content content = new Content(new ComingoutContentBuilder(me, this.latestGameInfo.getRole()));
		this.myTalks.add(content);
		this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()]++;
	}

	/**
	 * 占い対象を決定する．
	 * ロジックとしては，
	 * 1. 生きている人の中で
	 * 2. 占ったことのない人で
	 * 3. 自分以外の人で
	 * 4. 不確かな人？ <-- エントロピー的な観点で
	 */
	@Override
	public Agent divine() {
		List<Agent> agents = this.latestGameInfo.getAliveAgentList();
		agents = Arrays.asList(agents.stream()
				.filter(x -> x.getAgentIdx() != this.latestGameInfo.getAgent().getAgentIdx()).toArray(Agent[]::new));
		List<Agent> divinedAgents = Arrays.asList(seerJudges.stream().map(x -> x.getTarget()).toArray(Agent[]::new));
		agents = Arrays.asList(agents.stream().filter(x -> !divinedAgents.contains(x)).toArray(Agent[]::new));

		return choiceAgent(agents);
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// 配信された情報を追加
		if (gameInfo.getDivineResult() != null) {
			seerJudges.add(gameInfo.getDivineResult());
		}

		// 占い結果の発話情報を作成
		talkDivineResult();
	}

	private void talkDivineResult() {
		Agent me = this.latestGameInfo.getAgent();
		for (Judge judge : seerJudges) {
			Agent target = judge.getTarget();
			Species result = judge.getResult();
			if (this.talkMatrix[me.getAgentIdx() - 1][target.getAgentIdx() - 1][Topic.DIVINED.ordinal()] > 0) {
				continue;
			}
			Content content = new Content(new DivinedResultContentBuilder(target, result));
			this.myTalks.add(content);
			this.talkMatrix[me.getAgentIdx() - 1][target.getAgentIdx() - 1][Topic.DIVINED.ordinal()]++;
		}
	}

}
