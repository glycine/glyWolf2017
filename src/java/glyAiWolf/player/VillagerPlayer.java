package glyAiWolf.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * 村人．
 * 最初に村宣言．
 * 
 * @author "Haruhisa Ishida<haruhisa.ishida@gmail.com>"
 *
 */
public class VillagerPlayer extends BasePlayer {
	private Set<Agent> wolfs = new HashSet<>();

	@Override
	public void dayStart() {
		super.dayStart();
		if (this.myGameInfo.latestGameInfo.getDay() == 1) {
			// 自身のCO発言を追加する
			Agent me = this.myGameInfo.latestGameInfo.getAgent();
			Content content = new Content(new ComingoutContentBuilder(me, this.myGameInfo.latestGameInfo.getRole()));
			this.myDeclare.add(content);
		}
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		this.wolfs.clear();
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// もし自分に黒出ししたプレイヤーがいればwolf認定
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		Set<Agent> enemies = this.myGameInfo.getSeersByDivineResults(me, Species.WEREWOLF);
		this.wolfs.addAll(enemies);
	}

	/**
	 * 次の投票先を決定する
	 * 自分で決めるとき:
	 * 1. wolf
	 * 2．black
	 * 3. 複数CO占い
	 * 4. gray
	 * 5. estimate，vote少ない人
	 * * 狼っぽくなければ，自分で推測して決める
	 */
	@Override
	protected void decideNextVote() {
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		// enemyがいれば
		Set<Agent> enemies = new HashSet<>();
		enemies.addAll(this.wolfs);
		if (!enemies.isEmpty()) {
			enemies.retainAll(aliveAgents);
			enemies.remove(me);
			if (!enemies.isEmpty()) {
				this.updateNextVoteTarget(enemies.stream().findAny().get());
				return;
			}
		}
		// blackがいれば
		Set<Agent> black = this.myGameInfo.getBlack();
		if (!black.isEmpty()) {
			black.retainAll(aliveAgents);
			black.remove(me);
			if (!black.isEmpty()) {
				this.updateNextVoteTarget(black.stream().findAny().get());
				return;
			}
		}
		// COしている占いが複数いれば
		Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
		if (seers.size() >= 2) {
			seers.retainAll(aliveAgents);
			seers.remove(me);
			if (!seers.isEmpty()) {
				this.updateNextVoteTarget(seers.stream().findAny().get());
				return;
			}
		}
		// grayがいれば
		Set<Agent> gray = this.myGameInfo.getGray();
		if (!gray.isEmpty()) {
			gray.retainAll(aliveAgents);
			gray.remove(me);
			if (!gray.isEmpty()) {
				this.updateNextVoteTarget(gray.stream().findAny().get());
				return;
			}
		}
		// 無口な人がいれば
		Set<Agent> silents = this.myGameInfo.getSilents();
		if (!silents.isEmpty()) {
			silents.retainAll(aliveAgents);
			silents.remove(me);
			if (!silents.isEmpty()) {
				this.updateNextVoteTarget( silents.stream().findAny().get());
				return;
			}
		}
		// 生きているagentの中から自分以外をランダムセレクト
		aliveAgents.remove(me);
		this.updateNextVoteTarget(this.choiceAgent(aliveAgents));
		return;
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
		final double delta = 0.02;
		switch (content.getRole()) {
		case BODYGUARD:
		case MEDIUM:
		case SEER:
		case VILLAGER:
			// 村サイドの意見に対しては，何もしない
			break;
		case POSSESSED:
		case WEREWOLF:
		// 狼サイド
		{
			if (myTargetEstimateSide.equals(Role.WEREWOLF)) {
				// 同意見に対しては何もしない
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

	@Override
	protected void genEstimateTalk() {
		if (this.myGameInfo.currentDay >= 1 && this.myEstimate.isEmpty()) {
			List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		}

		// 現在の狼予想を発話生成する．
		// 過去の発話が発言されたときに限る
		// TODO: ここで信じている占い師を発話すべきか
		if (this.myGameInfo.latestGameInfo.getDay() >= 1) {
			if (this.myEstimate.isEmpty() && this.myVoteTarget != null) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(myVoteTarget, Role.WEREWOLF)));
			}
		}
	}
}
