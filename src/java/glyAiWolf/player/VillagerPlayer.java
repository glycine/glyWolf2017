package glyAiWolf.player;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;

public class VillagerPlayer extends BasePlayer {
	@Override
	public void dayStart() {
		super.dayStart();
		if (this.latestGameInfo.getDay() == 1) {
			// 自身のCO発言を追加する
			Agent me = this.latestGameInfo.getAgent();
			int myIndex = me.getAgentIdx() - 1;
			Content content = new Content(new ComingoutContentBuilder(me, this.latestGameInfo.getRole()));
			this.myDeclare.add(content);
			this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()]++;
		}
	}

	/**
	 * ※現状のロジックだと，値を変更すると村の勝率が下がるので何もさせない
	 * Estimate発話の処理．村人専用メソッド？
	 * 実装方針: roleのestimate結果が自分とどれだけあっているか．
	 * 合っている ->
	 * - 自分が村サイド -> 村サイドの可能性が高い
	 * - 自分が狼サイド -> 狼サイドの可能性が高い？
	 * 合ってない ->
	 * - 自分が村サイド -> 狼サイドの可能性が高い
	 * - 自分が狼サイド -> 村サイドの可能性が高い？
	 * 合っている/合ってないの判定手段は？ -> estimateのrole, targetをroleProbabilityと比較，
	 * どう値を上下させる？ -> 適当な値を加算
	 * 
	 * @param agent
	 * @param content
	 */
	@Override
	protected void handleEstimate(Agent agent, Content content) {
		Role myTargetEstimateSide = this.assumeSide(content.getTarget());
		final double delta = 0.00;
		switch (content.getRole()) {
		case BODYGUARD:
		case MEDIUM:
		case SEER:
		case VILLAGER:
		// 村サイドの意見
		{
			if (myTargetEstimateSide.equals(Role.VILLAGER)) {
				// 同意見 -> 村との見立てを増やす
				this.addVillagerPossibility(content.getTarget(), delta);
			} else {
				// 異なる意見 -> 狼との見立てを増やす
				this.addVillagerPossibility(content.getTarget(), -1.0 * delta);
			}
		}
			break;
		case POSSESSED:
		case WEREWOLF:
		// 狼サイド
		{
			if (myTargetEstimateSide.equals(Role.WEREWOLF)) {
				// 同意見 -> 村との見立てを増やす
				this.addVillagerPossibility(content.getTarget(), delta);
			} else {
				// 異なる意見 -> 狼との見立てを増やす
				this.addVillagerPossibility(content.getTarget(), -1.0 * delta);
			}
		}
			break;
		case FOX:
		case FREEMASON:
		default:
			// 使わない
			break;
		}
	}
}
