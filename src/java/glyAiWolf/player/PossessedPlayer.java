package glyAiWolf.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.VoteContentBuilder;
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
	private Set<Agent> divinedAgents = new HashSet<>();

	/**
	 * 日の開始の処理
	 * 1日目以降： 占いCOの発話生成, 占い対象生成
	 */
	@Override
	public void dayStart() {
		super.dayStart();
		Agent me = this.myGameInfo.latestGameInfo.getAgent();

		// 1日目に，偽の占いCOをする
		if (this.myGameInfo.latestGameInfo.getDay() == 1) {
			Content content = new Content(new ComingoutContentBuilder(me, Role.SEER));
			this.myDeclare.add(content);
		}
		// 1日目以降は，偽の占い結果を出し続ける
		if (this.myGameInfo.latestGameInfo.getDay() >= 1) {
			Set<Agent> targets = new HashSet<>();
			List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
			targets.addAll(targets);
			targets.remove(me);
			targets.removeAll(divinedAgents);
			if (!targets.isEmpty()) {
				Agent target = targets.stream().findAny().get();
				this.divinedAgents.add(target);
				Content divinedContent = new Content(new DivinedResultContentBuilder(target, Species.HUMAN));
				this.myDeclare.addLast(divinedContent);
			} else {
				aliveAgents.remove(me);
				Agent target = this.choiceAgent(aliveAgents);
				Content divinedContent = new Content(new DivinedResultContentBuilder(target, Species.HUMAN));
				this.myDeclare.addLast(divinedContent);

			}
		}
	}

	/**
	 * 狂人用の予想発話メソッド
	 * 対抗占いとgrayを狼という
	 * 
	 */
	@Override
	protected void genEstimateTalk() {
		if (this.myGameInfo.currentDay >= 1 && this.myEstimate.isEmpty()) {
			Agent me = this.myGameInfo.latestGameInfo.getAgent();
			Set<Agent> targets = new HashSet<>();
			// 対抗CO
			Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
			seers.remove(me);
			targets.addAll(seers);
			targets.addAll(this.myGameInfo.getGray());
			targets.remove(me);
			targets.retainAll(this.myGameInfo.latestGameInfo.getAliveAgentList());
			for (Agent target : targets) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(target, Role.WEREWOLF)));
			}
		}
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
	}

	/**
	 * 投票の決定
	 * 1. 対抗CO
	 * 2. gray
	 * 3. 無口
	 * 4. ランダム
	 */
	@Override
	protected void decideNextVote() {
		this.myVote.clear();
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		// 対抗COがいれば
		Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
		if (!seers.isEmpty()) {
			seers.retainAll(aliveAgents);
			seers.remove(me);
			if (!seers.isEmpty()) {
				this.myVoteTarget = seers.stream().findAny().get();
				this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
				return;
			}
		}
		// grayがいれば
		Set<Agent> gray = this.myGameInfo.getGray();
		if (!gray.isEmpty()) {
			gray.retainAll(aliveAgents);
			gray.remove(me);
			if (!gray.isEmpty()) {
				this.myVoteTarget = gray.stream().findAny().get();
				this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
				return;
			}
		}
		// 無口
		Set<Agent> silents = this.myGameInfo.getSilents();
		if (!silents.isEmpty()) {
			silents.retainAll(aliveAgents);
			silents.remove(me);
			if (!silents.isEmpty()) {
				this.myVoteTarget = silents.stream().findAny().get();
				this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
				return;
			}
		}
		// 生きているagentの中から自分以外をランダムセレクト
		aliveAgents.remove(me);
		this.myVoteTarget = this.choiceAgent(aliveAgents);
		this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
		return;
	}
}
