package glyAiWolf.player;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;

public class VillagerPlayer extends BasePlayer {
	@Override
	public void dayStart() {
		super.dayStart();
		if (this.latestGameInfo.getDay() == 1) {
			// 自身のCO発言を追加する
			Agent me = this.latestGameInfo.getAgent();
			int myIndex = me.getAgentIdx() - 1;
			Content content = new Content(new ComingoutContentBuilder(me, this.latestGameInfo.getRole()));
			this.myTalks.add(content);
			this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()]++;
		}
	}

}
