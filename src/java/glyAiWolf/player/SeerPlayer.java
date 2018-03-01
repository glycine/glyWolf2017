package glyAiWolf.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * 占い師の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class SeerPlayer extends BasePlayer {
	private Set<Agent> humans = new HashSet<>();
	private Set<Agent> wolfs = new HashSet<>();

	/**
	 * 1日目に自分の役職をCOする
	 */
	@Override
	public void dayStart() {
		super.dayStart();
		if (this.myGameInfo.latestGameInfo.getDay() == 1) {
			Agent me = this.myGameInfo.latestGameInfo.getAgent();
			Content content = new Content(new ComingoutContentBuilder(me, this.myGameInfo.latestGameInfo.getRole()));
			this.myDeclare.add(content);
		}
	}

	/**
	 * 投票対象を決めて投票する
	 * 生きているagentの中で，
	 * 1. 占いの結果黒
	 * 2. 対抗CO
	 * 3．無口
	 * 4. ランダム
	 */
	@Override
	protected void decideNextVote() {
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();

		// 占い結果黒がいれば
		Set<Agent> black = new HashSet<>();
		black.addAll(this.wolfs);
		if (!black.isEmpty()) {
			black.retainAll(aliveAgents);
			if (!black.isEmpty()) {
				this.updateNextVoteTarget(black.stream().findAny().get());
				return;
			}
		}
		// 対抗CO
		Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
		seers.remove(me);
		if (!seers.isEmpty()) {
			seers.retainAll(aliveAgents);
			if (!seers.isEmpty()) {
				this.updateNextVoteTarget(seers.stream().findAny().get());
				return;
			}
		}
		// 無口な人がいれば
		Set<Agent> silents = this.myGameInfo.getSilents();
		if (!silents.isEmpty()) {
			silents.retainAll(aliveAgents);
			silents.remove(me);
			if (!silents.isEmpty()) {
				this.updateNextVoteTarget(silents.stream().findAny().get());
				return;
			}
		}
		// 生きているagentの中から自分と白だしした人以外をランダムセレクト
		aliveAgents.remove(me);
		Agent target = aliveAgents.stream().findAny().get();
		aliveAgents.removeAll(this.humans);
		if (!aliveAgents.isEmpty()) {
			this.updateNextVoteTarget(aliveAgents.stream().findAny().get());
			return;
		} else {
			this.updateNextVoteTarget(target);
			return;
		}
	}

	/**
	 * 占い対象を決定する．
	 * ロジックとしては，
	 * 1. 生きている人の中で
	 * 2. 占ったことのない人で
	 * 3. 自分以外の人で
	 * 4. ほかの人が占った人 <- black, gray, whiteの和集合から，自分が占ったことのある人
	 * 5. 自分に対する占いリクエストが多い人？
	 */
	@Override
	public Agent divine() {
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		// 生きている人
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		// ほかの人が占ったことのある人で自分が占ったことのない人を出す
		Set<Agent> otherDivined = new HashSet<>();
		otherDivined.addAll(this.myGameInfo.getWhite());
		otherDivined.addAll(this.myGameInfo.getGray());
		otherDivined.addAll(this.myGameInfo.getBlack());
		otherDivined.removeAll(this.humans);
		otherDivined.removeAll(this.wolfs);
		otherDivined.remove(me);
		otherDivined.retainAll(aliveAgents);
		if(!otherDivined.isEmpty()) {
			return otherDivined.stream().findAny().get();
		}
		
		// 占ったことがある人を除いている
		aliveAgents.removeAll(this.humans);
		aliveAgents.removeAll(this.wolfs);
		aliveAgents.remove(me);
		// この段階で選択しておく
		Agent target = this.choiceAgent(aliveAgents);
		// 占いに対するリクエストがある人
		aliveAgents.retainAll(this.myGameInfo.getDiviationRequestCounts().keySet());

		if (!aliveAgents.isEmpty()) {
			return this.choiceAgent(aliveAgents);
		} else {
			if (target != null) {
				return target;
			} else {
				return me;
			}
		}
	}

	/**
	 * 黒の人と対抗COをWolfという
	 */
	@Override
	protected void genEstimateTalk() {
		if (this.myGameInfo.currentDay >= 1) {
			if (this.myEstimate.isEmpty()) {
				Agent me = this.myGameInfo.latestGameInfo.getAgent();
				List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
				Set<Agent> black = new HashSet<>();
				black.addAll(this.wolfs);
				black.retainAll(aliveAgents);
				Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
				seers.remove(me);
				for (Agent estimateWolf : black) {
					this.myEstimate.addLast(new Content(new EstimateContentBuilder(estimateWolf, Role.WEREWOLF)));
				}
				for (Agent estimateWolf : seers) {
					this.myEstimate.addLast(new Content(new EstimateContentBuilder(estimateWolf, Role.WEREWOLF)));
				}
			}
		}
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		this.humans.clear();
		this.wolfs.clear();
	}

	private void talkDivineResult() {
		for (Agent wolf : this.wolfs) {
			Content content = new Content(new DivinedResultContentBuilder(wolf, Species.WEREWOLF));
			this.myDeclare.addLast(content);
		}
		for (Agent human : this.humans) {
			Content content = new Content(new DivinedResultContentBuilder(human, Species.HUMAN));
			this.myDeclare.addLast(content);
		}
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// 配信された情報を追加
		Judge judge = gameInfo.getDivineResult();
		if (judge != null) {
			if (judge.getResult().equals(Species.HUMAN)) {
				// 人間追加
				this.humans.add(judge.getTarget());
			} else {
				// 狼追加
				this.wolfs.add(judge.getTarget());
			}
		}
		// 占い結果の発話情報を作成
		this.talkDivineResult();
	}
}
