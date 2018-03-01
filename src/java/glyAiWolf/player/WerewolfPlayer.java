package glyAiWolf.player;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.AttackContentBuilder;
import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.IdentContentBuilder;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.client.lib.VoteContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

import glyAiWolf.lib.WolfGameInfo;

public class WerewolfPlayer extends BasePlayer {
	// 狼サイドからみたgameInfo
	protected WolfGameInfo wolfGameInfo;
	// 自分のfakeCo
	private Role fakeRole;
	// 自分の襲撃対象
	private Agent attackTarget;
	// 囁き予定リスト．囁けば順に消えていく
	private Deque<Content> myWhispers = new ConcurrentLinkedDeque<>();

	/**
	 * 偽の霊媒師を実行し，結果を発話する．
	 * 狼の場合，仲間がわかっているので真実を言うことができる
	 */
	private void actFakeMedium() {
		Agent executedAgent = this.wolfGameInfo.latestGameInfo.getExecutedAgent();
		if (executedAgent != null) {
			if (this.wolfGameInfo.werewolfs.contains(executedAgent)) {
				// 狼だったので，狼判定出す
				this.myDeclare.addLast(new Content(new IdentContentBuilder(executedAgent, Species.WEREWOLF)));
			} else {
				this.myDeclare.addLast(new Content(new IdentContentBuilder(executedAgent, Species.HUMAN)));
			}
		}
	}

	/**
	 * 偽の占いを実行し，結果を発話する．
	 * ランダムで前日襲撃されたプレイヤーか，生きているプレイヤーからまだ占っていない人を対象として人間判定を出す
	 */
	private void actFakeSeer() {
		if (this.wolfGameInfo.latestGameInfo.getDay() == 0) {
			// まだ占い結果は出せない
			return;
		}
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		// 生き残っているagentの中で，自分以外で占ったことのない人

		List<Agent> targets = Arrays
				.asList(this.wolfGameInfo.latestGameInfo.getAliveAgentList().stream().filter(y -> !me.equals(y))
						.filter(x -> this.wolfGameInfo.divineResults[me.getAgentIdx() - 1][x.getAgentIdx() - 1] != null)
						.toArray(Agent[]::new));
		if (targets.isEmpty()) {
			// 全員占ったことがある-> 生きている人からランダムに人判定を出す
			this.myDeclare.addLast(new Content(new DivinedResultContentBuilder(
					this.choiceAgent(this.wolfGameInfo.latestGameInfo.getAliveAgentList()), Species.HUMAN)));
		} else {
			if (this.wolfGameInfo.latestGameInfo.getAttackedAgent() == null) {
				// 昨夜襲撃が失敗している -> 生きている人からランダムに人判定を出す
				Agent target = this.choiceAgent(targets);
				this.myDeclare.addLast(new Content(new DivinedResultContentBuilder(target, Species.HUMAN)));
			} else {
				// 昨夜襲撃が成功 -> 襲撃した人に対して人判定を出す
				this.myDeclare.addLast(new Content(new DivinedResultContentBuilder(
						this.wolfGameInfo.latestGameInfo.getAttackedAgent(), Species.HUMAN)));
			}
		}
	}

	@Override
	public Agent attack() {
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		if (this.wolfGameInfo.attackStatus[me.getAgentIdx() - 1] == null) {
			// なぜか定まっていない．生きている人のうち，人狼以外からランダムに選択して返す
			Set<Agent> werewolfs = this.wolfGameInfo.werewolfs;
			return this.choiceAgent(Arrays.asList(this.wolfGameInfo.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> !werewolfs.contains(x)).toArray(Agent[]::new)));
		} else {
			return this.wolfGameInfo.attackStatus[me.getAgentIdx() - 1];
		}
	}

	/**
	 * fakeCoの状態をチェックする．
	 * trueが返る状態: 自身のCO予定が何かしらの値が入っていて，他のfakeCO予定と重なっていない
	 * 
	 * @return
	 */
	private boolean checkFakeCoStatus() {
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		if (this.wolfGameInfo.coResults[me.getAgentIdx() - 1] == null) {
			return false;
		}
		for (Agent agent : this.wolfGameInfo.werewolfs) {
			if (agent.getAgentIdx() == me.getAgentIdx()) {
				continue;
			}
			if (this.wolfGameInfo.coResults[me.getAgentIdx() - 1]
					.equals(this.wolfGameInfo.coResults[agent.getAgentIdx() - 1])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * trueを返す条件:
	 * - 自分のAttack先が決まっている
	 * - 狼の間で投票先が一致している
	 * 
	 * @return
	 */
	private boolean checkNextAttackedAgentStatus() {
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		if (this.wolfGameInfo.attackStatus[me.getAgentIdx() - 1] == null) {
			return false;
		}
		if (Arrays.asList(this.wolfGameInfo.attackStatus).stream().filter(x -> x != null).distinct().count() != 1) {
			return false;
		}
		return true;
	}

	/**
	 * trueを返す条件:
	 * - 自分の投票先が決まっている
	 * - 狼の間で投票先が一致している
	 * 
	 * @return
	 */
	@Override
	protected boolean checkNextVoteAgentsStatus() {
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		if (this.wolfGameInfo.attackStatus[me.getAgentIdx() - 1] == null) {
			return false;
		}
		if (this.wolfGameInfo.werewolfs.stream().map(x -> this.wolfGameInfo.attackStatus[x.getAgentIdx() - 1])
				.filter(y -> y != null).distinct().count() != 1) {
			return false;
		}
		return true;
	}

	@Override
	public void dayStart() {
		super.dayStart();
		this.wolfGameInfo.dayChange();
		Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
		// COに関して
		if (this.wolfGameInfo.latestGameInfo.getDay() == 1) {
			// fakeRoleは決まっているはずなので，偽役職のCOを行う
			if (this.fakeRole != null) {
				this.myDeclare.addLast(new Content(new ComingoutContentBuilder(me, this.fakeRole)));
			}
		}
		if (this.wolfGameInfo.latestGameInfo.getDay() >= 1) {
			// fakeRokeに基づいた行動を行う
			if (Role.SEER.equals(this.fakeRole)) {
				// 占いの行動を行う
				this.actFakeSeer();
			} else if (Role.MEDIUM.equals(this.fakeRole)) {
				// 霊媒師の行動を行う
				this.actFakeMedium();
			}
		}
	}

	/**
	 * 偽のCO役職を決定する。
	 * 変に役職COすると勝率が下がるので、とりあえず、村人を騙ることにする
	 */
	private void decideFakeCO() {
		if (this.wolfGameInfo.currentDay == 0) {
			Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
			this.fakeRole = Role.VILLAGER;
			this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.VILLAGER)));
		}
	}

	/**
	 * 次の襲撃対象を決定する．
	 * 1. white
	 * 2. 狩人CO
	 * 3. 村人CO
	 * 4.
	 */
	private void decideNextAttackedAgent() {
		// 他のプレイヤーの襲撃予定リストを作成する
		Map<Agent, Integer> attackStatus = this.wolfGameInfo.getAttackStatus();
		if (!attackStatus.isEmpty()) {
			// 同調する
			this.attackTarget = attackStatus.keySet().stream().findAny().get();
			this.myWhispers.addLast(new Content(new AttackContentBuilder(this.attackTarget)));
			return;
		} else {
			// Whiteがいれば
			List<Agent> aliveAgents = this.wolfGameInfo.latestGameInfo.getAliveAgentList();
			Set<Agent> whites = this.wolfGameInfo.getWhite();
			whites.retainAll(aliveAgents);
			whites.removeAll(this.wolfGameInfo.aliveWerewolfs);
			if (!whites.isEmpty()) {
				Agent target = whites.stream().findAny().get();
				this.attackTarget = target;
				this.myWhispers.addLast(new Content(new AttackContentBuilder(this.attackTarget)));
				return;
			}
			// 狩人COがいれば
			Set<Agent> bodyguards = this.wolfGameInfo.getCOAgents(Role.BODYGUARD);
			bodyguards.retainAll(aliveAgents);
			bodyguards.removeAll(this.wolfGameInfo.aliveWerewolfs);
			if (!bodyguards.isEmpty()) {
				Agent target = bodyguards.stream().findAny().get();
				this.attackTarget = target;
				this.myWhispers.addLast(new Content(new AttackContentBuilder(this.attackTarget)));
				return;
			}
			// 村人CO or noCO
			Set<Agent> villagers = this.wolfGameInfo.getCOAgents(Role.VILLAGER);
			Set<Agent> noCOs = this.wolfGameInfo.getCOAgents(null);
			villagers.addAll(noCOs);
			villagers.retainAll(aliveAgents);
			villagers.removeAll(this.wolfGameInfo.aliveWerewolfs);
			if (!villagers.isEmpty()) {
				Agent target = villagers.stream().findAny().get();
				this.attackTarget = target;
				this.myWhispers.addLast(new Content(new AttackContentBuilder(this.attackTarget)));
				return;
			} else {
				aliveAgents.removeAll(this.wolfGameInfo.aliveWerewolfs);
				if (!aliveAgents.isEmpty()) {
					Agent target = this.choiceAgent(aliveAgents);
					this.attackTarget = target;
					this.myWhispers.addLast(new Content(new AttackContentBuilder(this.attackTarget)));
				}
				return;
			}
		}

	}

	/**
	 * 次の投票先を決定する．
	 * 1. 同調
	 * 2. black
	 * 3. 無口
	 * 4．ランダム
	 * TODO: 要改善
	 */
	protected void decideNextVote() {
		this.myVote.clear();
		// とりあえず，現状のVote状況を取得する
		Map<Agent, Integer> voteStatus = this.wolfGameInfo.getVoteStatusCount(this.wolfGameInfo.currentDay);
		// wolfリスト
		Set<Agent> aliveWolfs = this.wolfGameInfo.aliveWerewolfs;
		// vote状況から狼を除く
		for (Agent wolf : aliveWolfs) {
			if (voteStatus.containsKey(wolf)) {
				voteStatus.remove(wolf);
			}
		}
		// もし村への投票提案があれば，同調する
		if (!voteStatus.isEmpty()) {
			Agent voteTarget = null;
			int voteCount = -1;
			for (Agent target : voteStatus.keySet()) {
				if (voteCount < voteStatus.get(target)) {
					voteTarget = target;
					voteCount = voteStatus.get(target);
				}
			}
			this.myVoteTarget = voteTarget;
			this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
			return;
		} else {
			List<Agent> aliveAgents = this.wolfGameInfo.latestGameInfo.getAliveAgentList();
			// blackがいれば
			Set<Agent> black = this.wolfGameInfo.getBlack();
			if (!black.isEmpty()) {
				black.retainAll(aliveAgents);
				if (!black.isEmpty()) {
					this.myVoteTarget = black.stream().findAny().get();
					this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
					return;
				}
			}
			// 無口
			Set<Agent> silents = this.wolfGameInfo.getSilents();
			if (!silents.isEmpty()) {
				silents.retainAll(aliveAgents);
				if (!silents.isEmpty()) {
					this.myVoteTarget = silents.stream().findAny().get();
					this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
					return;
				}
			}
			// 生きている村人からランダムに提案する
			aliveAgents.removeAll(this.wolfGameInfo.aliveWerewolfs);
			if (!aliveAgents.isEmpty()) {
				this.myVoteTarget = this.choiceAgent(aliveAgents);
				this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
				return;
			}
		}

	}

	/**
	 * 自分以外の
	 * blackに対して狼推測する
	 */
	@Override
	protected void genEstimateTalk() {
		if (this.wolfGameInfo.currentDay >= 1 && this.myEstimate.isEmpty()) {
			Agent me = this.wolfGameInfo.latestGameInfo.getAgent();
			List<Agent> aliveAgents = this.wolfGameInfo.latestGameInfo.getAliveAgentList();
			Set<Agent> targets = new HashSet<>();
			targets.addAll(this.wolfGameInfo.getBlack());
			targets.remove(me);
			targets.retainAll(aliveAgents);
			for (Agent target : targets) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(target, Role.WEREWOLF)));
			}
		}
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		this.wolfGameInfo = new WolfGameInfo(gameInfo, gameSetting);
		this.fakeRole = null;
	}

	/**
	 * 発話と囁きの方針
	 * 発話:
	 * - 1日目以降: 投票予定者を議論し，
	 * 
	 * 囁き:
	 * - 0日目: COの方針を議論する
	 * - 1日目以降: 襲撃予定者を議論する
	 */
	@Override
	public void update(GameInfo gameInfo) {
		super.update(gameInfo);
		this.wolfGameInfo.update(gameInfo);

		// 発話生成
		// 投票予定のAgentを決め，提案する
		if (this.wolfGameInfo.latestGameInfo.getDay() >= 1) {
			if (!this.checkNextVoteAgentsStatus()) {
				if (randomAction()) {
					decideNextVote();
				}
			}
		}

		// 囁き生成
		// CO予定の役職を決め，宣言する
		if (this.wolfGameInfo.latestGameInfo.getDay() == 0) {
			if (!checkFakeCoStatus()) {
				if (randomAction()) {
					decideFakeCO();
				}
			}
		}
		// 襲撃予定の役職を決め，提案する
		if (this.wolfGameInfo.latestGameInfo.getDay() >= 1) {
			if (!checkNextAttackedAgentStatus()) {
				if (randomAction()) {
					decideNextAttackedAgent();
				}
			}
		}
	}

	@Override
	public String whisper() {
		if (this.myWhispers.isEmpty()) {
			Content skip = new Content(new SkipContentBuilder());
			return skip.getText();
		}
		Content content = this.myWhispers.pop();
		return content.getText();
	}
}
