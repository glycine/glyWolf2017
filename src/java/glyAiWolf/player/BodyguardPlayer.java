package glyAiWolf.player;

import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.GuardedAgentContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * 狩人用のクラス．護衛のみ実装．残りは村人と共通．
 * 
 * TODO: CO関係の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class BodyguardPlayer extends BasePlayer {
	private boolean talkedResult = false;
	private Deque<Agent> guardTargets = new ConcurrentLinkedDeque<>();
	private Set<Agent> enemies = new HashSet<>();

	@Override
	public void dayStart() {
		super.dayStart();
		this.talkedResult = false;
		// 1日目に村人とCOする
		if (this.myGameInfo.latestGameInfo.getDay() == 1) {
			Content content = new Content(
					new ComingoutContentBuilder(this.myGameInfo.latestGameInfo.getAgent(), Role.VILLAGER));
			this.myDeclare.addLast(content);
		}
	}

	/**
	 * 投票対象を決めて投票する
	 * 生きているagentの中で，
	 * 1. enemy
	 * 2. black
	 * 3. 対抗CO
	 * 4. gray
	 * 5. 無口
	 * 6. ランダム
	 */
	@Override
	protected void decideNextVote() {
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		
		// enemyがいれば
		Set<Agent> myEnemies = new HashSet<>();
		myEnemies.addAll(this.enemies);
		if(!myEnemies.isEmpty()) {
			myEnemies.retainAll(aliveAgents);
			myEnemies.remove(me);
			if(!myEnemies.isEmpty()) {
				this.updateNextVoteTarget(myEnemies.stream().findAny().get());
				return;

			}
		}
		// blackがいれば
		Set<Agent> black = this.myGameInfo.getBlack();
		if (!black.isEmpty()) {
			black.retainAll(aliveAgents);
			if (!black.isEmpty()) {
				this.updateNextVoteTarget(black.stream().findAny().get());
				return;
			}
		}
		// 対抗COがいれば
		Set<Agent> bodyguards = this.myGameInfo.getCOAgents(Role.BODYGUARD);
		if (!bodyguards.isEmpty()) {
			bodyguards.retainAll(aliveAgents);
			bodyguards.remove(me);
			if (!bodyguards.isEmpty()) {
				this.updateNextVoteTarget(bodyguards.stream().findAny().get());
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
		// 無口
		Set<Agent> silents = this.myGameInfo.getSilents();
		if (!silents.isEmpty()) {
			silents.retainAll(aliveAgents);
			silents.remove(me);
			if (!silents.isEmpty()) {
				this.updateNextVoteTarget(silents.stream().findAny().get());
				return;
			}
		}
		// 生きているagentの中から自分以外をランダムセレクト
		aliveAgents.remove(me);
		this.updateNextVoteTarget(this.choiceAgent(aliveAgents));
		return;
	}

	/**
	 * 対抗COとblackに対して狼推測する
	 */
	@Override
	protected void genEstimateTalk() {
		if (this.myGameInfo.currentDay >= 1 && this.myEstimate.isEmpty()) {
			Agent me = this.myGameInfo.latestGameInfo.getAgent();
			Set<Agent> targets = new HashSet<>();
			// 対抗CO
			Set<Agent> bodyguards = this.myGameInfo.getCOAgents(Role.BODYGUARD);
			bodyguards.remove(me);
			targets.addAll(bodyguards);
			targets.addAll(this.myGameInfo.getBlack());
			targets.remove(me);
			targets.retainAll(this.myGameInfo.latestGameInfo.getAliveAgentList());
			for (Agent target : targets) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(target, Role.WEREWOLF)));
			}
		}
	}

	/**
	 * 護衛対象を決定する．
	 * ロジックとしては，生きている人の中で，
	 * 1. 1人占い
	 * 2. 1人霊媒
	 * 3. white
	 * 4. 占いco/霊媒co/村coからランダムで
	 * TODO: リクエストの受付
	 * 
	 */
	@Override
	public Agent guard() {
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		// 生きている人
		List<Agent> aliveAgents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		
		// １人占い
		Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
		if (seers.size() == 1) {
			seers.retainAll(aliveAgents);
			if (!seers.isEmpty()) {
				Agent target = seers.stream().findAny().get();
				this.guardTargets.addLast(target);
				return target;
			}
		}
		// 1人霊媒
		Set<Agent> mediums = this.myGameInfo.getCOAgents(Role.MEDIUM);
		if (mediums.size() == 1) {
			mediums.retainAll(aliveAgents);
			if (!mediums.isEmpty()) {
				Agent target = mediums.stream().findAny().get();
				this.guardTargets.addLast(target);
				return target;
			}
		}
		// white
		Set<Agent> whites = this.myGameInfo.getWhite();
		if (!whites.isEmpty()) {
			whites.retainAll(aliveAgents);
			if (!whites.isEmpty()) {
				Agent target = whites.stream().findAny().get();
				this.guardTargets.addLast(target);
				return target;
			}
		}
		// 占いco/霊媒co/村coから自分以外をランダムで
		Set<Agent> guardTargets = new HashSet<>();
		guardTargets.addAll(this.myGameInfo.getCOAgents(Role.SEER));
		guardTargets.addAll(this.myGameInfo.getCOAgents(Role.MEDIUM));
		guardTargets.addAll(this.myGameInfo.getCOAgents(Role.VILLAGER));
		guardTargets.retainAll(aliveAgents);
		guardTargets.remove(me);
		if (!guardTargets.isEmpty()) {
			Agent target = guardTargets.stream().findAny().get();
			this.guardTargets.addLast(target);
			return target;
		} else {
			aliveAgents.remove(me);
			Agent target = aliveAgents.stream().findAny().get();
			this.guardTargets.addLast(target);
			return target;
		}
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		this.guardTargets.clear();
		this.enemies.clear();
	}

	protected void talkGuardResult() {
		if (!talkedResult) {
			Agent guardedAgent = this.myGameInfo.latestGameInfo.getGuardedAgent();
			if (!this.guardTargets.isEmpty() && this.guardTargets.getLast().equals(guardedAgent)) {
				// 護衛結果を言い，なおかつCOもする
				Content content = new Content(
						new ComingoutContentBuilder(this.myGameInfo.latestGameInfo.getAgent(), Role.BODYGUARD));
				this.myDeclare.addLast(content);
				Content guardedContent = new Content(new GuardedAgentContentBuilder(guardedAgent));
				this.myDeclare.addLast(guardedContent);
			}
		}
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// もし自分に黒出ししたプレイヤーがいればwolf認定
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		this.enemies.addAll(this.myGameInfo.getSeersByDivineResults(me, Species.WEREWOLF));
		
		talkGuardResult();
	}
}
