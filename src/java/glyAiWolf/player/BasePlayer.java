package glyAiWolf.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.client.lib.VoteContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class BasePlayer implements Player {
	// ランダム行動する際の閾値．[0.0, 1.0)の値をとり，randomでこの値以下が出れば行動しない
	public static final double RANDOMACTION_THRESHOLD = 0.25;
	// 狼と判断する際の閾値．[0.0, 1.0)の値をとり，rolePossibilityがこの値以上であれば，狼っぽいと判断する
	public static final double WEREWOLF_PROB_THRESHOLD = 0.7;
	// 日ごとのgameInfo
	protected Map<Integer, GameInfo> gameInfos = new TreeMap<>();
	// 最新のgameInfo
	protected GameInfo latestGameInfo = null;
	// 今回のゲームのgameSetting, initialize時にコピーし，後はそのまま
	protected GameSetting gameSetting = null;
	// 追放されたAgents
	protected List<Agent> executedAgents = new ArrayList<>();
	// 襲撃されたAgents
	protected List<Agent> attackedAgents = new ArrayList<>();
	// 処理した他プレイヤーの発話リスト
	protected Deque<Talk> processedTalks = new ConcurrentLinkedDeque<>();
	// 発話予定リスト．発話すれば順に消えていく
	protected Deque<Content> myTalks = new ConcurrentLinkedDeque<>();
	// 次に投票する予定のAgent．agentIndexで引く
	protected Agent[] nextVoteAgents = null;
	// 各プレイヤーの各役職の可能性
	protected double[][] rolePossibility;
	// 各プレイヤーの各プレイヤーに対する行動行列
	protected int[][][] talkMatrix;
	// 各プレイヤー(役職持ち)の判別結果記録と検証用の行列
	protected Species[][] verifyMatrix;
	// estimate結果の発話済みフラグ
	protected boolean saidEstimation = false;

	/**
	 * rolePossibility行列に基づいて，agentのRoleを推測するもの
	 * 
	 * @param agent
	 * @return
	 */
	protected Role assumeRole(Agent agent) {
		int agentIndex = agent.getAgentIdx() - 1;
		Role result = null;
		double prob = -1.0;
		for (Role role : Role.values()) {
			if (prob < this.rolePossibility[agentIndex][role.ordinal()]) {
				prob = this.rolePossibility[agentIndex][role.ordinal()];
				result = role;
			}
		}
		return result;
	}

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public Agent attack() {
		return null;
	}

	/**
	 * trueを返す条件
	 * - 自分の投票先が決まっている
	 * - 自分の投票先が最大である
	 * 
	 * @return
	 */
	protected boolean checkNextVoteAgentsStatus() {
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextVoteAgents[me.getAgentIdx() - 1] == null) {
			return false;
		}
		// 投票表明先に出現するagents
		List<Agent> targets = Arrays.asList(
				Arrays.asList(this.nextVoteAgents).stream().filter(x -> x != null).distinct().toArray(Agent[]::new));
		List<Long> counts = new ArrayList<>();
		for (Agent target : targets) {
			counts.add(Arrays.asList(this.nextVoteAgents).stream().filter(x -> x != null)
					.filter(y -> y.getAgentIdx() == target.getAgentIdx()).count());
		}

		// 得票数が最大のtargetを取得する
		Agent maxVotedAgent = null;
		long maxVoteNum = -1L;
		for (int i = 0; i < targets.size(); ++i) {
			if (maxVoteNum < counts.get(i)) {
				maxVoteNum = counts.get(i);
				maxVotedAgent = targets.get(i);
			}
		}

		// 得票数最大 != 自身の投票予定先でなければ問題あるとする
		if (maxVotedAgent.getAgentIdx() != this.nextVoteAgents[me.getAgentIdx() - 1].getAgentIdx()) {
			return false;
		}

		return true;
	}

	/**
	 * 与えられたagentリストからランダムに選択して返す
	 * 
	 * @param agents
	 * @return
	 */
	protected Agent choiceAgent(List<Agent> agents) {
		if (agents == null || agents.isEmpty()) {
			return null;
		}
		int index = (int) Math.floor(Math.random() * (double) agents.size());
		return agents.get(index);
	}

	@Override
	public void dayStart() {
		// nextVoteAgentsをクリアする
		Arrays.fill(this.nextVoteAgents, null);
		// estimate発言フラグをクリアする
		this.saidEstimation = false;

		// 襲撃されたAgentとAttackされたAgentを把握して記録する．
		// 現在のルールでは狐と共有者がいないため，追放されずに死んだ == 狼に襲撃と判断することができる
		if (this.latestGameInfo.getDay() >= 1) {
			GameInfo preGameInfo = this.gameInfos.get(this.latestGameInfo.getDay() - 1);
			List<Agent> deadAgents = Arrays.asList(preGameInfo.getAliveAgentList().stream()
					.filter(x -> !this.latestGameInfo.getAliveAgentList().contains(x)).toArray(Agent[]::new));
			if (!deadAgents.isEmpty()) {
				for (Agent deadAgent : deadAgents) {
					if (deadAgent.equals(this.latestGameInfo.getExecutedAgent())) {
						this.executedAgents.add(deadAgent);
					} else {
						this.attackedAgents.add(deadAgent);
						// attackされたagentはすべてHuman ->
						// もしそのagentに対して狼判定を出したプレイヤーは嘘をついた->狼？
						for (Agent agent : this.latestGameInfo.getAgentList()) {
							if (this.verifyMatrix[agent.getAgentIdx() - 1][deadAgent.getAgentIdx() - 1] != null) {
								if (!Species.HUMAN.equals(this.verifyMatrix[agent.getAgentIdx()-1][deadAgent.getAgentIdx()-1])){										
									// 矛盾発生 -> agentは嘘をついていた？ (霊媒師が信じられることが前提)
									Role[] villagerRoles = Arrays.asList(Role.values()).stream()
											.filter(x -> x.getSpecies().equals(Species.HUMAN)).toArray(Role[]::new);
									double prob = 0.0;
									for (Role role : villagerRoles) {
										prob += this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()];
										this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()] = 0.0;
									}
									this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] += prob;
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * 次の投票先を決定する
	 * * 誰も決めていないとき -> 自分で推測して決める
	 * * だれかが投票先を決めているとき，自分視点で狼っぽければ同調する
	 * * 狼っぽくなければ，自分で推測して決める
	 */
	protected void decideNextVote() {
		Agent me = this.latestGameInfo.getAgent();
		// 他のプレイヤーの投票予定リストを作成する．この段階で自分は抜かす
		List<Agent> targets = Arrays.asList(
				this.latestGameInfo.getAliveAgentList().stream().filter(x -> x.getAgentIdx() != me.getAgentIdx())
						.map(y -> this.nextVoteAgents[y.getAgentIdx() - 1]).filter(z -> z != null)
						.filter(w -> w.getAgentIdx() != me.getAgentIdx()).distinct().toArray(Agent[]::new));
		if (targets.isEmpty()) {
			// 他のプレイヤーはまだ投票予定を決めていない -> 自分で決めて発案する
			// 生きていて，最も狼らしいAgent
			List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> x.getAgentIdx() != me.getAgentIdx()).toArray(Agent[]::new));
			Agent target = me;
			double wolfProb = 0.0;
			for (Agent agent : agents) {
				if (this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] > wolfProb) {
					wolfProb = this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()];
					target = agent;
				}
			}
			this.nextVoteAgents[me.getAgentIdx() - 1] = target;
			this.myTalks.addLast(new Content(new VoteContentBuilder(target)));
		} else if (targets.size() == 1 && this.rolePossibility[targets.get(0).getAgentIdx() - 1][Role.WEREWOLF
				.ordinal()] < WEREWOLF_PROB_THRESHOLD) {
			// 候補は1つあるが，自分視点で狼らしくない -> 自分で決めて発案する
			// 生きていて，最も狼らしいAgent
			List<Agent> agents = Arrays.asList(this.latestGameInfo.getAliveAgentList().stream()
					.filter(x -> x.getAgentIdx() != me.getAgentIdx()).toArray(Agent[]::new));
			Agent target = me;
			double wolfProb = 0.0;
			for (Agent agent : agents) {
				if (this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] > wolfProb) {
					wolfProb = this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()];
					target = agent;
				}
			}
			this.nextVoteAgents[me.getAgentIdx() - 1] = target;
			this.myTalks.addLast(new Content(new VoteContentBuilder(target)));
		} else {
			// targetsのうち最も狼らしいプレイヤーに投票する
			Agent target = me;
			double wolfProb = 0.0;
			for (Agent agent : targets) {
				if (this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] > wolfProb) {
					wolfProb = this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()];
					target = agent;
				}
			}
			this.nextVoteAgents[me.getAgentIdx() - 1] = target;
			this.myTalks.addLast(new Content(new VoteContentBuilder(target)));
		}
	}

	/**
	 * roleMapを推測し，発話する
	 */
	protected void estimateRoleMap() {
		List<Agent> agents = this.latestGameInfo.getAliveAgentList();
		List<Role> assumedRoles = Arrays.asList(agents.stream().map(x -> this.assumeRole(x)).toArray(Role[]::new));
		for (int i = 0; i < agents.size(); ++i) {
			Role assumedRole = assumedRoles.get(i);
			if (assumedRole.equals(Role.SEER) || assumedRole.equals(Role.WEREWOLF)) {
				this.myTalks.addLast(new Content(new EstimateContentBuilder(agents.get(i), assumedRole)));
			}
		}
	}

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public Agent divine() {
		return null;
	}

	@Override
	public void finish() {
		// TODO 自動生成されたメソッド・スタブ
	}

	/**
	 * 各playerの各役職に対する可能性の行列を作成する． <br/>
	 * 各行が各playerに対応し，各列が役職の可能性を表す
	 * 
	 * @param roleNumMap
	 * @return
	 */
	protected double[][] genRolePossibility(GameInfo gameInfo, GameSetting gameSetting) {
		// 領域作成，初期化
		double[][] rolePossibility = new double[gameSetting.getPlayerNum()][Role.values().length];
		for (int i = 0; i < gameSetting.getPlayerNum(); ++i) {
			for (int j = 0; j < Role.values().length; ++j) {
				rolePossibility[i][j] = 0.0;
			}
		}

		// 自分の役職は確定しているので，自分がその役職である確率を1とする
		int myIndex = gameInfo.getAgent().getAgentIdx() - 1;
		Role myRole = gameInfo.getRole();
		rolePossibility[myIndex][myRole.ordinal()] = 1.0;

		// ダブりを含めた，自分以外の役職のリストアップ
		List<Role> otherRoles = new ArrayList<>();
		for (Role role : Role.values()) {
			if (!role.equals(myRole)) {
				int roleNum = gameSetting.getRoleNum(role);
				for (int i = 0; i < roleNum; ++i) {
					otherRoles.add(role);
				}
			} else {
				int otherRoleNum = gameSetting.getRoleNum(role) - 1;
				for (int i = 0; i < otherRoleNum; ++i) {
					otherRoles.add(role);
				}
			}
		}
		double base = 1.0 / (double) (otherRoles.size());
		for (int i = 0; i < gameSetting.getPlayerNum(); ++i) {
			if (i == myIndex) {
				continue;
			}
			for (Role role : otherRoles) {
				rolePossibility[i][role.ordinal()] += base;
			}
		}

		return rolePossibility;
	}

	protected int[][][] genTalkMatrix(GameInfo gameInfo, GameSetting gameSetting) {
		int playerNum = gameSetting.getPlayerNum();
		int topicNum = Topic.values().length;
		int[][][] talkMatrix = new int[playerNum][playerNum][topicNum];
		for (int i = 0; i < talkMatrix.length; ++i) {
			for (int j = 0; j < talkMatrix[i].length; ++j) {
				for (int k = 0; k < talkMatrix[i][j].length; ++k) {
					talkMatrix[i][j][k] = 0;
				}
			}
		}
		return talkMatrix;
	}

	@Override
	public String getName() {
		return "glycine";
	}

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public Agent guard() {
		return null;
	}

	/**
	 * Comingout発言の処理．
	 * まず，各プレイヤーの投票候補に関するものがリセットされる(COによって結果が変わると想定)
	 * <ol>
	 * <li>村/狼サイドの役職中の可能性が集約される</li>
	 * <li>もし可能性がない役職についてCOしている場合は狼サイドと判定</li>
	 * <li>他サイドについては考慮しない</li>
	 * </ol>
	 * TODO: 他サイドのCOの処理
	 * 
	 * @param content
	 */
	protected void handleComingout(Agent agent, Content content) {
		int playerIndex = agent.getAgentIdx() - 1;
		Role coRole = content.getRole();
		List<Role> coTeamRoles = Arrays.asList(Arrays.asList(Role.values()).stream()
				.filter(x -> x.getTeam().equals(coRole.getTeam())).filter(y -> !y.equals(coRole)).toArray(Role[]::new));
		double prob = 0.0;
		for (Role role : coTeamRoles) {
			prob += this.rolePossibility[playerIndex][role.ordinal()];
			this.rolePossibility[playerIndex][role.ordinal()] = 0.0;
		}

		if (this.rolePossibility[playerIndex][coRole.ordinal()] > 0.0) {
			// 村人サイドの可能性がある
			this.rolePossibility[playerIndex][coRole.ordinal()] += prob;
		} else {
			// 村人サイドの可能性がない->現在は狼の可能性が高いと判定する
			this.rolePossibility[playerIndex][Role.WEREWOLF.ordinal()] += prob;
		}

		// 投票候補をリセット
		Arrays.fill(this.nextVoteAgents, null);
		// estimate発言済みフラグをリセット
		this.saidEstimation = false;

	}

	/**
	 * 占い結果を処理する．
	 * - 占い結果が真実の可能性がある場合は，占われた対象がそのteamSideである可能性を上げる．
	 * - 上げる割合は1/2
	 * - 占い結果が偽である場合は，占った対象を狼の割合を上げる
	 * 
	 * @param agent
	 * @param content
	 */
	protected void handleDevined(Agent agent, Content content) {
		// とりあえず記録
		this.verifyMatrix[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = content.getResult();

		int playerIndex = agent.getAgentIdx() - 1;
		int targetIndex = content.getTarget().getAgentIdx() - 1;
		Species species = content.getResult();
		// まず判定されたspeciesである可能性を判定する
		Role[] targetRoles = Arrays.asList(Role.values()).stream().filter(x -> x.getSpecies().equals(species))
				.toArray(Role[]::new);
		boolean checkResult = false;
		for (Role targetRole : targetRoles) {
			if (this.rolePossibility[targetIndex][targetRole.ordinal()] > 0.0) {
				checkResult = true;
				break;
			}
		}
		if (checkResult) {
			// 占い結果はあり得る -> 占い結果を踏まえて確率を変える
			// 村サイドの確率をp, 狼サイドの確率が1-pであり，占い師の確率がqであるときに，村サイドの占い結果が出たとすると，
			// 新しい占い結果は，p + (1-p) * qとする
			double seerProb = this.rolePossibility[playerIndex][Role.SEER.ordinal()];
			double targetProb = 0.0;
			for (Role targetRole : targetRoles) {
				targetProb += this.rolePossibility[targetIndex][targetRole.ordinal()];
			}
			double opposeProb = 1.0 - targetProb;

			for (Role targetRole : targetRoles) {
				this.rolePossibility[targetIndex][targetRole.ordinal()] += opposeProb
						* this.rolePossibility[targetIndex][targetRole.ordinal()] / targetProb * seerProb;
			}
			Role[] opposeRoles = Arrays.asList(Role.values()).stream().filter(x -> !x.getSpecies().equals(species))
					.toArray(Role[]::new);
			for (Role opposeRole : opposeRoles) {
				this.rolePossibility[targetIndex][opposeRole
						.ordinal()] -= this.rolePossibility[targetIndex][opposeRole.ordinal()] * seerProb;
			}
		} else {
			// 占い結果はありえない -> 占った人は狼の可能性が高いと判定する
			Role[] villagerRoles = Arrays.asList(Role.values()).stream()
					.filter(x -> x.getSpecies().equals(Species.HUMAN)).toArray(Role[]::new);
			double prob = 0.0;
			for (Role role : villagerRoles) {
				prob += this.rolePossibility[playerIndex][role.ordinal()];
				this.rolePossibility[playerIndex][role.ordinal()] = 0.0;
			}
			this.rolePossibility[playerIndex][Role.WEREWOLF.ordinal()] += prob;
		}
	}

	protected void handleIdentified(Agent identifier, Content content) {
		for (Agent agent : this.latestGameInfo.getAgentList()) {
			if (this.verifyMatrix[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] != null) {
				if (!content.getResult()
						.equals(this.verifyMatrix[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1])) {
					// 矛盾発生 -> agentは嘘をついていた？ (霊媒師が信じられることが前提)
					Role[] villagerRoles = Arrays.asList(Role.values()).stream()
							.filter(x -> x.getSpecies().equals(Species.HUMAN)).toArray(Role[]::new);
					double prob = 0.0;
					for (Role role : villagerRoles) {
						prob += this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()];
						this.rolePossibility[agent.getAgentIdx() - 1][role.ordinal()] = 0.0;
					}
					this.rolePossibility[agent.getAgentIdx() - 1][Role.WEREWOLF.ordinal()] += prob;
				}
			}
		}
	}

	/**
	 * 他プレイヤーのtalkを処理する
	 * 
	 * @param talk
	 */
	protected void handleTalk(Talk talk) {
		Agent agent = talk.getAgent();
		Content content = new Content(talk.getText());
		switch (content.getTopic()) {
		case AGREE:
			break;
		case ATTACK:
			break;
		case COMINGOUT:
			handleComingout(agent, content);
			break;
		case DISAGREE:
			break;
		case DIVINATION:
			break;
		case DIVINED:
			this.handleDevined(agent, content);
			break;
		case ESTIMATE:
			// 本来他人の推測を入れる
			break;
		case GUARD:
			break;
		case GUARDED:
			break;
		case IDENTIFIED:
			this.handleIdentified(agent, content);
			break;
		case OPERATOR:
			break;
		case OVER:
			break;
		case SKIP:
			break;
		case VOTE:
			this.handleVote(agent, content);
			break;
		default:
			break;
		}
	}

	protected void handleVote(Agent agent, Content content) {
		this.nextVoteAgents[agent.getAgentIdx() - 1] = content.getTarget();
	}

	/**
	 * 配信されるgameInfoとgameSettingを保存
	 */
	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		// 前の情報を引き継がないようにするためクリア
		this.gameInfos.clear();
		this.processedTalks.clear();
		this.executedAgents.clear();
		this.attackedAgents.clear();
		this.saidEstimation = false;

		// 値のコピー
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
		this.latestGameInfo = gameInfo;
		this.gameSetting = gameSetting.clone();
		// 各役職の可能性の行列を作成する
		this.nextVoteAgents = new Agent[gameSetting.getPlayerNum()];
		Arrays.fill(this.nextVoteAgents, null);
		this.rolePossibility = genRolePossibility(gameInfo, gameSetting);
		this.talkMatrix = genTalkMatrix(gameInfo, gameSetting);
		this.verifyMatrix = new Species[gameSetting.getPlayerNum()][gameSetting.getPlayerNum()];
		for (int i = 0; i < this.verifyMatrix.length; ++i) {
			Arrays.fill(this.verifyMatrix[i], null);
		}
	}

	/**
	 * ランダムでtrueかfalseを返す．ランダムで行動を決定するため
	 * 
	 * @return
	 */
	protected boolean randomAction() {
		if (Math.random() < RANDOMACTION_THRESHOLD) {
			return false;
		} else {
			return true;
		}
	}

	protected void showRoleProbability() {
		if (this.latestGameInfo.getAgent().getAgentIdx() != 5) {
			return;
		}

		for (Role role : Role.values()) {
			System.err.print(role + ", ");
		}
		System.err.println("");
		for (double[] row : rolePossibility) {
			for (double elem : row) {
				System.err.print(elem + ", ");
			}
			System.err.println("");
		}
	}

	/**
	 * 発言処理．queueにたまった発言リストを処理．なくなればskipする
	 */
	@Override
	public String talk() {
		if (this.myTalks.isEmpty()) {
			Content skip = new Content(new SkipContentBuilder());
			return skip.toString();
		}
		Content content = this.myTalks.pop();
		return content.getText();
	}

	/**
	 * 配信されるgameInfoを保存
	 */
	@Override
	public void update(GameInfo gameInfo) {
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
		this.latestGameInfo = gameInfo;
		Deque<Talk> newTalks = new ConcurrentLinkedDeque<>();
		for (Talk talk : gameInfo.getTalkList()) {
			if (processedTalks.isEmpty() || !talk.equals(processedTalks.getLast())) {
				newTalks.addLast(talk);
			}
		}
		while (!newTalks.isEmpty()) {
			Talk talk = newTalks.pollFirst();
			handleTalk(talk);
			this.processedTalks.addLast(talk);
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
		// 現在の狼予想と信じている占い師を発話する
		if (this.latestGameInfo.getDay() >= 1) {
			if (!this.saidEstimation) {
				estimateRoleMap();
				this.saidEstimation = true;
			}
		}

		// デバッグ用の出力
		// this.showRoleProbability();
	}

	/**
	 * 投票行動．
	 * - すでに投票行動を決めているなら，それに投票
	 * - 決まっていない場合は，生きているAgentのうち，最も狼らしいAgentに投票する．
	 */
	@Override
	public Agent vote() {
		Agent me = this.latestGameInfo.getAgent();
		if (this.nextVoteAgents[me.getAgentIdx() - 1] != null) {
			return this.nextVoteAgents[me.getAgentIdx() - 1];
		}
		List<Agent> aliveAgents = this.latestGameInfo.getAliveAgentList();
		double wolfProb = 0.0;
		Agent result = this.latestGameInfo.getAgent();
		for (Agent agent : aliveAgents) {
			int agentIndex = agent.getAgentIdx() - 1;
			if (this.rolePossibility[agentIndex][Role.WEREWOLF.ordinal()] > wolfProb) {
				wolfProb = this.rolePossibility[agentIndex][Role.WEREWOLF.ordinal()];
				result = agent;
			}
		}
		return result;
	}

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public String whisper() {
		return null;
	}
}
