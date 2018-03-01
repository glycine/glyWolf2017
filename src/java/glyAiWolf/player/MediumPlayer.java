package glyAiWolf.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.IdentContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * 霊媒師の実装
 * 
 * @author "Haruhisa Ishida(haruhisa.ishida@gmail.com)"
 *
 */
public class MediumPlayer extends BasePlayer {
	private Set<Agent> humans = new HashSet<>();
	private Set<Agent> wolfs = new HashSet<>();
	private Set<Agent> enemies = new HashSet<>();

	@Override
	public void dayStart() {
		super.dayStart();
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		if (this.myGameInfo.latestGameInfo.getDay() == 1) {
			// 自身の役職をCOする
			Content content = new Content(new ComingoutContentBuilder(me, this.myGameInfo.latestGameInfo.getRole()));
			this.myDeclare.add(content);
		}
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		this.humans.clear();
		this.wolfs.clear();
		this.enemies.clear();
	}

	/**
	 * 投票対象を決めて投票する
	 * 生きているagentの中で，
	 * 1. wolf
	 * 2. fake占い
	 * 3. black
	 * 4. 対抗CO
	 * 5. gray
	 * 6. 無口
	 * 7．ランダム
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
		// fake占いがいれば
		Set<Agent> fakeSeer = this.findFakeSeer();
		if (!fakeSeer.isEmpty()) {
			fakeSeer.retainAll(aliveAgents);
			if (!fakeSeer.isEmpty()) {
				this.updateNextVoteTarget(fakeSeer.stream().findAny().get());
				return;
			}
		}
		// black
		Set<Agent> black = this.myGameInfo.getBlack();
		if (!black.isEmpty()) {
			black.retainAll(aliveAgents);
			black.remove(me);
			if (!black.isEmpty()) {
				this.updateNextVoteTarget(black.stream().findAny().get());
				return;
			}
		}
		// 対抗CO
		Set<Agent> mediums = this.myGameInfo.getCOAgents(Role.MEDIUM);
		if (!mediums.isEmpty()) {
			mediums.retainAll(aliveAgents);
			mediums.remove(me);
			if (!mediums.isEmpty()) {
				this.updateNextVoteTarget(mediums.stream().findAny().get());
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
	 * 対抗COとfakeSeer, blackに対して狼推測する
	 */
	@Override
	protected void genEstimateTalk() {
		if (this.myGameInfo.currentDay >= 1 && this.myEstimate.isEmpty()) {
			Agent me = this.myGameInfo.latestGameInfo.getAgent();
			Set<Agent> targets = new HashSet<>();
			targets.addAll(this.findFakeSeer());
			Set<Agent> mediums = this.myGameInfo.getCOAgents(Role.MEDIUM);
			mediums.remove(me);
			targets.addAll(mediums);
			targets.addAll(this.myGameInfo.getBlack());
			targets.retainAll(this.myGameInfo.latestGameInfo.getAliveAgentList());
			for (Agent target : targets) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(target, Role.WEREWOLF)));
			}
		}
	}

	/**
	 * 矛盾した占い結果を出した占い士を出す
	 * 
	 * @return
	 */
	private Set<Agent> findFakeSeer() {
		Set<Agent> seers = this.myGameInfo.getCOAgents(Role.SEER);
		List<Agent> fakeSeers = Arrays.asList(seers.stream().filter(x -> !this.checkSeer(x)).toArray(Agent[]::new));
		Set<Agent> result = new HashSet<>();
		result.addAll(fakeSeers);
		return result;
	}

	private boolean checkSeer(Agent seer) {
		boolean result = true;
		for (Agent human : this.humans) {
			Species s = this.myGameInfo.divineResults[seer.getAgentIdx() - 1][human.getAgentIdx() - 1];
			if (s != null && Species.WEREWOLF.equals(s)) {
				// 嘘の占い結果
				result = false;
			}
		}
		for (Agent wolf : this.wolfs) {
			Species s = this.myGameInfo.divineResults[seer.getAgentIdx() - 1][wolf.getAgentIdx() - 1];
			if (s != null && Species.HUMAN.equals(s)) {
				// 嘘の占い結果
				result = false;
			}
		}
		return result;
	}

	private void talkMediumResult() {
		for (Agent wolf : this.wolfs) {
			Content content = new Content(new IdentContentBuilder(wolf, Species.WEREWOLF));
			this.myDeclare.addLast(content);
		}
		for (Agent human : this.humans) {
			Content content = new Content(new IdentContentBuilder(human, Species.HUMAN));
			this.myDeclare.addLast(content);
		}
	}

	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		// 配信された情報を追加
		// もし自分に黒出ししたプレイヤーがいればwolf認定
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		this.enemies.addAll(this.myGameInfo.getSeersByDivineResults(me, Species.WEREWOLF));
		
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
		// 発話情報を作成
		talkMediumResult();
	}
}
