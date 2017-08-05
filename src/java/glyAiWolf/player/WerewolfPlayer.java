package glyAiWolf.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.IdentContentBuilder;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.client.lib.Topic;
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

	@Override
	public void dayStart() {
		super.dayStart();
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
	 * 偽のCO役職を決定する
	 */
	private void decideFakeCO() {
		Agent me = this.latestGameInfo.getAgent();
		List<Role> otherCoRoles = new ArrayList<>();
		for (Agent agent : this.latestGameInfo.getAliveAgentList()) {
			if (agent.getAgentIdx() != me.getAgentIdx() && this.fakeCoRoles[agent.getAgentIdx() - 1] != null) {
				otherCoRoles.add(this.fakeCoRoles[agent.getAgentIdx() - 1]);
			}
		}
		if (otherCoRoles.isEmpty()) {
			// co予定のroleがないので，占いCO予定とする
			this.fakeCoRoles[me.getAgentIdx() - 1] = Role.SEER;
			this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.SEER)));
		} else if (otherCoRoles.size() == 1) {
			// 占いと霊媒のいずれかCO予定にないものをCO予定とする
			if (!otherCoRoles.contains(Role.SEER)) {
				this.fakeCoRoles[me.getAgentIdx() - 1] = Role.SEER;
				this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.SEER)));
			} else if (!otherCoRoles.contains(Role.MEDIUM)) {
				this.fakeCoRoles[me.getAgentIdx() - 1] = Role.MEDIUM;
				this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.MEDIUM)));
			}
		} else {
			// COは他プレイヤーに任せ，村を装うこととする
			this.fakeCoRoles[me.getAgentIdx() - 1] = Role.VILLAGER;
			this.myWhispers.addLast(new Content(new ComingoutContentBuilder(me, Role.MEDIUM)));
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
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);

		// 狼固有のデータ構造の初期化
		this.fakeCoRoles = new Role[gameSetting.getPlayerNum()];
		Arrays.fill(this.fakeCoRoles, null);
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
	 * 発話と囁きの方針
	 * 発話:
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

		// 囁き生成
		// TODO: CO予定の役職を決め，宣言する
		if (this.latestGameInfo.getDay() == 0) {
			if (!checkFakeCoStatus()) {
				if (randomAction()) {
					decideFakeCO();
				}
			}
		}
		// TODO: 襲撃予定の役職を決め，提案する
	}

	/**
	 * 投票対象のAgentを決める．
	 * 1. 生きていて
	 * 2. 狼ではなくて
	 * 3. とりあえずランダム
	 */
	@Override
	public Agent vote() {
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
