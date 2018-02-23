package glyAiWolf.player;

import java.util.HashSet;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.IdentContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;

/**
 * 霊媒師の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class MediumPlayer extends VillagerPlayer {
	private Set<Judge> mediumJudges = new HashSet<>();

	@Override
	public void dayStart() {
		super.dayStart();
		Agent me = this.latestGameInfo.getAgent();
		if (this.latestGameInfo.getDay() == 1) {
			// 自身の役職をCOする
			int myIndex = me.getAgentIdx() - 1;
			Content content = new Content(new ComingoutContentBuilder(me, this.latestGameInfo.getRole()));
			this.myTalks.add(content);
			this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()]++;
		}
	}

	/**
	 * TODO: judgeの情報を踏まえた行列の更新
	 */
	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// 配信された情報を追加
		if (gameInfo.getMediumResult() != null) {
			this.mediumJudges.add(gameInfo.getMediumResult());
		}
		// 発話情報を作成
		talkMediumResult();
	}

	private void talkMediumResult() {
		Agent me = this.latestGameInfo.getAgent();
		for (Judge judge : mediumJudges) {
			Agent target = judge.getTarget();
			Species result = judge.getResult();
			if (this.talkMatrix[me.getAgentIdx() - 1][target.getAgentIdx() - 1][Topic.IDENTIFIED.ordinal()] > 0) {
				continue;
			}
			Content content = new Content(new IdentContentBuilder(target, result));
			this.myTalks.add(content);
			this.talkMatrix[me.getAgentIdx() - 1][target.getAgentIdx() - 1][Topic.IDENTIFIED.ordinal()]++;
		}
	}
}
