package glyAiWolf.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.AttackContentBuilder;
import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.IdentContentBuilder;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.client.lib.VoteContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class WerewolfPlayer extends BasePlayer {
	// 処理した他狼の囁きリスト
	protected Deque<Talk> processedWhispers = new ConcurrentLinkedDeque<>();
	// 囁き予定リスト．囁けば順に消えていく
	private Deque<Content> myWhispers = new ConcurrentLinkedDeque<>();
	// 他の狼のfakeCO役職リスト．agentIndexで引く
	private Role[] fakeCoRoles = null;
	// 次に襲撃するつもりのAgent．agentIndexで引く
	private Agent[] nextAttackedAgents = null;

	/**
	 * 偽の霊媒師を実行し，結果を発話する．
	 * 狼の場合，仲間がわかっているので真実を言うことができる
	 */
	private void actFakeMedium() {
		Agent executedAgent = this.latestGameInfo.getExecutedAgent();
		if (executedAgent != null) {
			if (this.fakeCoRoles[executedAgent.getAgentIdx() - 1] != null) {
				// 狼だったので，狼判定出す
				this.myTalks.addLast(new Content(new IdentContentBuilder(executedAgent, Species.WEREWOLF)));
			} else {
				this.myTalks.addLast(new Content(new IdentContentBuilder(executedAgent, Species.HUMAN)));
			}
		}
	}
	
	

	/**
	 * 偽の占いを実行し，結果を発話する．
	 * ランダムで前日襲撃されたプレイヤーか，生きているプレイヤーからまだ占っていない人を対象として人間判定を出す
	 */
	private void actFakeSeer() {
		if (this.latestGameInfo.getDay() == 0) {
			// まだ占い結果は出せない
			return;
		}
		Agent me = this.latestGameInfo.getAgent();
		List<Agent> targets = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream().filter(y -> !me.equals(y))
				.filter(x -> this.talkMatrix[me.getAgentIdx() - 1][x.getAgentIdx() - 1][Topic.DIVINED.ordinal()] == 0)
				.toArray(Agent[]::new));
		if (targets.isEmpty()) {
			// 全員占ったことがある-> 生きている人からランダムに人判定を出す
			this.myTalks.addLast(new Content(new DivinedResultContentBuilder(
					this.choiceAgent(this.latestGameInfo.getAliveAgentList()), Species.HUMAN)));
		} else {
			if (this.latestGameInfo.getAttackedAgent() == null) {
				// 昨夜襲撃が失敗している -> 生きている人からランダムに人判定を出す
				Agent target = this.choiceAgent(targets);
				this.myTalks.addLast(new Content(new DivinedResultContentBuilder(target, Species.HUMAN)));
			} else {
				// 昨夜襲撃が成功 -> 襲撃した人に対して人判定を出す
				this.myTalks.addLast(new Content(
						new DivinedResultContentBuilder(this.latestGameInfo.getAttackedAgent(), Species.HUMAN)));
			}
		}
	}

	@Override
	public Agent attack() {
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextAttackedAgents[me.getAgentIdx() - 1] == null) {
			// なぜか定まっていない．生きている人のうち，人狼以外からランダムに選択して返す
			List<Agent> werewolfs = this.getWerewolfs();
			return this.choiceAgent(Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> !werewolfs.contains(x)).toArray(Agent[]::new)));
		} else {
			return this.nextAttackedAgents[me.getAgentIdx() - 1];
		}
	}

	/**
	 * fakeCoの状態をチェックする．
	 * trueが返る状態: 自身のCO予定が何かしらの値が入っていて，他のfakeCO予定と重なっていない
	 * 
	 * @return
	 */
	private boolean checkFakeCoStatus() {
		Agent me = this.latestGameInfo.getAgent();
		if (this.fakeCoRoles[me.getAgentIdx() - 1] == null) {
			return false;
		}
		for (Agent agent : this.latestGameInfo.getAgentList()) {
			if (agent.getAgentIdx() == me.getAgentIdx()) {
				continue;
			}
			if (this.fakeCoRoles[me.getAgentIdx() - 1].equals(this.fakeCoRoles[agent.getAgentIdx() - 1])) {
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
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextAttackedAgents[me.getAgentIdx() - 1] == null) {
			return false;
		}
		if (Arrays.asList(this.nextAttackedAgents).stream().filter(x -> x != null).distinct().count() != 1) {
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
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextVoteAgents[me.getAgentIdx() - 1] == null) {
			return false;
		}
		if (this.getWerewolfs().stream().map(x -> this.nextVoteAgents[x.getAgentIdx() - 1]).filter(y -> y != null)
				.distinct().count() != 1) {
			return false;
		}
		return true;
	}

	@Override
	public void dayStart() {
		super.dayStart();
		// nextAttackedAgents をクリアする
		Arrays.fill(this.nextAttackedAgents, null);
		Agent me = this.latestGameInfo.getAgent();
		// COに関して
		if (this.latestGameInfo.getDay() == 1) {
			// fakeRoleは決まっているはずなので，偽役職のCOを行う
			if (this.fakeCoRoles[me.getAgentIdx() - 1] != null) {
				this.myTalks
						.addLast(new Content(new ComingoutContentBuilder(me, this.fakeCoRoles[me.getAgentIdx() - 1])));
				this.talkMatrix[me.getAgentIdx() - 1][me.getAgentIdx() - 1][Topic.COMINGOUT.ordinal()]++;
			}
		}
		if (this.latestGameInfo.getDay() >= 1) {
			// fakeRokeに基づいた行動を行う
			if (Role.SEER.equals(this.fakeCoRoles[me.getAgentIdx() - 1])) {
				// 占いの行動を行う
				this.actFakeSeer();
			} else if (Role.MEDIUM.equals(this.fakeCoRoles[me.getAgentIdx() - 1])) {
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
		Agent me = this.latestGameInfo.getAgent();
		List<Role> otherCoRoles = new ArrayList<>();
		// 生きているエージェントのうち、自分以外の狼のCOを整理する
		for (Agent agent : this.latestGameInfo.getAliveAgentList()) {
			if (agent.getAgentIdx() != me.getAgentIdx() && this.fakeCoRoles[agent.getAgentIdx() - 1] != null) {
				otherCoRoles.add(this.fakeCoRoles[agent.getAgentIdx() - 1]);
			}
		}
		this.fakeCoRoles[me.getAgentIdx()-1] = Role.VILLAGER;
		this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.VILLAGER)));
	}

	/**
	 * 次の投票先を決定する．
	 * 基本的に，他の狼と投票先を合わせる．
	 */
	protected void decideNextVote() {
		Agent me = this.latestGameInfo.getAgent();
		// 他の狼プレイヤーの投票予定リストを作成する
		List<Agent> targets = Arrays.asList(getWerewolfs().stream().filter(x -> x.getAgentIdx() != me.getAgentIdx())
				.map(y -> this.nextVoteAgents[y.getAgentIdx() - 1]).filter(z -> z != null).toArray(Agent[]::new));
		if (targets.isEmpty()) {
			// 他の狼は投票予定を決めていない->自分で決めて，発案する
			// 生きていて，狼サイドではなくて，占いの可能性が一番高いAgent
			List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> !getWerewolfs().contains(x)).toArray(Agent[]::new));
			Agent target = me;
			for (Agent agent : agents) {
				if (this.rolePossibility[target.getAgentIdx() - 1][Role.SEER
						.ordinal()] <= this.rolePossibility[agent.getAgentIdx() - 1][Role.SEER.ordinal()]) {
					target = agent;
				}
			}
			this.nextVoteAgents[me.getAgentIdx() - 1] = target;
			this.myTalks.addLast(new Content(new VoteContentBuilder(target)));
		} else {
			// とりあえず，同調する
			// TODO: 他の狼の投票先が問題ないかチェック
			this.nextVoteAgents[me.getAgentIdx() - 1] = targets.get(0);
			this.myTalks.addLast(new Content(new VoteContentBuilder(targets.get(0))));
		}
	}

	/**
	 * 狼用の予想発話メソッド
	 * 
	 */
	@Override
	protected void estimateRoleMap() {
		for (Agent werewolf : getWerewolfs()) {
			if (!this.latestGameInfo.getAliveAgentList().contains(werewolf)) {
				continue;
			}
			Role fakeCoRole = this.fakeCoRoles[werewolf.getAgentIdx() - 1];
			if (fakeCoRole != null && Role.SEER.equals(fakeCoRole)) {
				this.myTalks.addLast(new Content(new EstimateContentBuilder(werewolf, Role.SEER)));
			}
		}
		List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
				.filter(x -> !getWerewolfs().contains(x)).toArray(Agent[]::new));
		List<Role> assumedRole = Arrays.asList(agents.stream().map(x -> this.assumeRole(x)).toArray(Role[]::new));
		for (int i = 0; i < agents.size(); ++i) {
			if (assumedRole.equals(Role.WEREWOLF)) {
				this.myTalks.addLast(new Content(new EstimateContentBuilder(agents.get(i), Role.WEREWOLF)));
			}
		}
	}

	/**
	 * 次の襲撃対象を決定する．
	 */
	private void decideNextAttackedAgent() {
		Agent me = this.latestGameInfo.getAgent();
		// 他のプレイヤーの襲撃予定リストを作成する
		List<Agent> targets = new ArrayList<>();
		for (Agent werewolf : getWerewolfs()) {
			if (me.getAgentIdx() == werewolf.getAgentIdx()) {
				continue;
			}
			if (this.nextAttackedAgents[werewolf.getAgentIdx() - 1] != null) {
				targets.add(this.nextAttackedAgents[werewolf.getAgentIdx() - 1]);
			}
		}
		if (targets.isEmpty()) {
			// 他のプレイヤーは襲撃予定を決めていない -> 自分で決めて，発案する
			// 生きていて，狼サイドではなくて，村人の可能性が一番高いAgent
			List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> !getWerewolfs().contains(x)).toArray(Agent[]::new));
			Agent target = me;
			for (Agent agent : agents) {
				if (this.rolePossibility[target.getAgentIdx() - 1][Role.VILLAGER
						.ordinal()] <= this.rolePossibility[agent.getAgentIdx() - 1][Role.VILLAGER.ordinal()]) {
					target = agent;
				}
			}
			this.nextAttackedAgents[me.getAgentIdx() - 1] = target;
			this.myWhispers.addLast(new Content(new AttackContentBuilder(target)));
		} else {
			// とりあえず，同調する
			this.nextAttackedAgents[me.getAgentIdx() - 1] = targets.get(0);
			this.myWhispers.addLast(new Content(new AttackContentBuilder(targets.get(0))));
		}
	}

	private void detectWerewolf(Agent agent) {
		// agentのroleProbを，狼1, 残りを0にする
		for (Role role : Role.values()) {
			if (role.equals(Role.WEREWOLF)) {
				this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()] = 1.0;
			} else {
				this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()] = 0.0;
			}
		}
		// 発見済みの狼リスト
		List<Agent> detectedWerewolfs = Arrays.asList(this.latestGameInfo.getAgentList().stream()
				.filter(x -> this.rolePossibility[x.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] >= 1.0)
				.toArray(Agent[]::new));
		// 発見した狼の数が，gameSettingと同数であれば，全員発見，roleProbの非狼を0にし，その分の確率を村人に振る
		if (detectedWerewolfs.size() == this.gameSetting.getRoleNum(Role.WEREWOLF)) {
			List<Agent> humans = Arrays.asList(this.latestGameInfo.getAgentList().stream()
					.filter(x -> !detectedWerewolfs.contains(x)).toArray(Agent[]::new));
			for (Agent human : humans) {
				this.rolePossibility[human.getAgentIdx() - 1][Role.VILLAGER
						.ordinal()] += this.rolePossibility[human.getAgentIdx() - 1][Role.WEREWOLF.ordinal()];
				this.rolePossibility[human.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] = 0.0;
			}
		}
	}

	/**
	 * 狼リストを返す．fakeCoRolesをもとに算出するため，機能するのは1日目以降
	 * 
	 * @return
	 */
	private List<Agent> getWerewolfs() {
		return Arrays.asList(this.latestGameInfo.getAgentList().stream()
				.filter(x -> this.fakeCoRoles[x.getAgentIdx() - 1] != null).toArray(Agent[]::new));
	}

	private void handleWAttack(Agent agent, Content content) {
		this.nextAttackedAgents[agent.getAgentIdx() - 1] = content.getTarget();
	}

	/**
	 * 囁きのCO宣言をfakeCoRolesに反映する
	 */
	private void handleWComingout(Agent agent, Content content) {
		this.fakeCoRoles[agent.getAgentIdx() - 1] = content.getRole();
	}

	/**
	 * 囁きの処理．
	 * 発話のagentから，狼を見つけ，roleProbを更新する
	 * 
	 * @param whisper
	 */
	private void handleWhisper(Talk whisper) {
		Agent agent = whisper.getAgent();
		// 囁きをもとに狼発見情報を更新する
		detectWerewolf(agent);
		Content content = new Content(whisper.getText());
		switch (content.getTopic()) {
		case AGREE:
			break;
		case ATTACK:
			handleWAttack(agent, content);
			break;
		case COMINGOUT:
			handleWComingout(agent, content);
			break;
		case DISAGREE:
			break;
		case DIVINATION:
			break;
		case DIVINED:
			break;
		case ESTIMATE:
			break;
		case GUARD:
			break;
		case GUARDED:
			break;
		case IDENTIFIED:
			break;
		case OPERATOR:
			break;
		case OVER:
			break;
		case SKIP:
			break;
		case VOTE:
			break;
		default:
			break;
		}
	}

	@Override
	protected void handleComingout(Agent agent, Content content) {
		super.handleComingout(agent, content);

		// 襲撃先候補をリセット
		Arrays.fill(this.nextAttackedAgents, null);
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);

		// 狼固有のデータ構造の初期化
		this.fakeCoRoles = new Role[gameSetting.getPlayerNum()];
		Arrays.fill(this.fakeCoRoles, null);
		this.nextAttackedAgents = new Agent[gameSetting.getPlayerNum()];
		Arrays.fill(this.nextAttackedAgents, null);
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

		// 囁きの処理
		Deque<Talk> newWhispers = new ConcurrentLinkedDeque<>();
		for (Talk talk : gameInfo.getWhisperList()) {
			if (processedWhispers.isEmpty() || !talk.equals(processedWhispers.getLast())) {
				newWhispers.addLast(talk);
			}
		}
		while (!newWhispers.isEmpty()) {
			Talk whisper = newWhispers.pollFirst();
			handleWhisper(whisper);
			this.processedWhispers.addLast(whisper);
		}

		// 発話生成
		// 投票予定のAgentを決め，提案する
		if (this.latestGameInfo.getDay() >= 1) {
			if (!this.checkNextVoteAgentsStatus()) {
				if (randomAction()) {
					decideNextVote();
				}
			}
		}

		// 囁き生成
		// CO予定の役職を決め，宣言する
		if (this.latestGameInfo.getDay() == 0) {
			if (!checkFakeCoStatus()) {
				if (randomAction()) {
					decideFakeCO();
				}
			}
		}
		// 襲撃予定の役職を決め，提案する
		if (this.latestGameInfo.getDay() >= 1) {
			if (!checkNextAttackedAgentStatus()) {
				if (randomAction()) {
					decideNextAttackedAgent();
				}
			}
		}
	}

	/**
	 * 投票対象のAgentを決める．
	 * - すでに議論で決まっている場合はそれを
	 * - 決まっていない場合
	 * 1. 生きていて
	 * 2. 狼ではなくて
	 * 3. とりあえずランダム
	 */
	@Override
	public Agent vote() {
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextVoteAgents[me.getAgentIdx() - 1] != null) {
			return this.nextVoteAgents[me.getAgentIdx() - 1];
		}

		List<Agent> targets = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
				.filter(x -> this.rolePossibility[x.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] < 1.0)
				.toArray(Agent[]::new));
		if (!targets.isEmpty()) {
			return this.choiceAgent(targets);
		} else {
			return this.choiceAgent(this.latestGameInfo.getAliveAgentList());
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
