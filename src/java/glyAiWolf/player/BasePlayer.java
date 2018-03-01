package glyAiWolf.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

import glyAiWolf.lib.BaseGameInfo;

/**
 * 全てのプレイヤーのベースとなるクラス．
 * roleProbabilityで役職を推定し，
 * 
 * @author "Haruhisa Ishida<haruhisa.ishida@gmail.com>"
 *
 */
public abstract class BasePlayer implements Player {
	// ランダム行動する際の閾値．[0.0, 1.0)の値をとり，randomでこの値以下が出れば行動しない
	public static final double RANDOMACTION_THRESHOLD = 0.25;
	// 狼と判断する際の閾値．[0.0, 1.0)の値をとり，rolePossibilityがこの値以上であれば，狼っぽいと判断する
	public static final double WEREWOLF_PROB_THRESHOLD = 0.7;
	// probabilityの最小値．これ以下の場合，確定とみなし，値をいじらないようにする
	public static final double MIN_POSSIBILITY = 0.01;
	// 自分用にcustomしたgameInfo
	protected BaseGameInfo myGameInfo = null;
	// 発話予定リスト．発話すれば順に消えていく
	protected Deque<Content> myDeclare = new ConcurrentLinkedDeque<>();
	protected Deque<Content> myEstimate = new ConcurrentLinkedDeque<>();
	protected Deque<Content> myVote = new ConcurrentLinkedDeque<>();
	// 各プレイヤーの各役職の可能性．[agentIndex][roleIndex]で引く
	protected double[][] rolePossibility;
	// estimate結果の発話済みフラグ
	protected boolean saidEstimation = false;
	// talk時のskip count
	protected int talkSkipCount = 0;
	// 投票先
	protected Agent myVoteTarget;

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
	 * 与えられたagentのSideを，自分のrolePossibilityに基づいて判別して返す.
	 * 
	 * @param agent
	 * @return 村サイドであればRole.VILLAGERを，狼サイドであればRole.WEREWOLFを返す．
	 */
	protected Role assumeSide(Agent target) {
		int taretIndex = target.getAgentIdx() - 1;
		double wolfProbability = 0.0;
		wolfProbability += this.rolePossibility[taretIndex][Role.WEREWOLF.ordinal()];
		wolfProbability += this.rolePossibility[taretIndex][Role.POSSESSED.ordinal()];
		if (wolfProbability >= 0.5) {
			return Role.WEREWOLF;
		} else {
			return Role.VILLAGER;
		}
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
		if (this.myVoteTarget == null) {
			return false;
		}
		// 投票宣言状況を取得し，その中で最大の得票数
		Map<Agent, Integer> voteStatusCount = this.myGameInfo.getVoteStatusCount(this.myGameInfo.currentDay);
		if (voteStatusCount.isEmpty()) {
			return false;
		}
		int maxCount = voteStatusCount.values().stream().max(new Comparator<Integer>() {
			@Override
			public int compare(Integer arg0, Integer arg1) {
				return Integer.compare(arg0, arg1);
			}
		}).get();
		Set<Agent> maxCountAgents = new HashSet<>();
		for (Agent agent : voteStatusCount.keySet()) {
			if (maxCount == voteStatusCount.get(agent)) {
				maxCountAgents.add(agent);
			}
		}

		// 得票数最大のAgentsに自分の投票予定先が含まれていないなければ，問題あるとする
		if (!maxCountAgents.contains(this.myVoteTarget)) {
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

	/**
	 * 1日の開始。
	 * TODO: 投票先に応じて、村人らしい/狼らしいを判別する処理を追加したい
	 */
	@Override
	public void dayStart() {
		this.myGameInfo.dayChange();
		// estimate発言フラグをクリアする
		this.saidEstimation = false;
		// talkSkipCountを初期化する
		this.talkSkipCount = 0;
		// 投票対象をクリアする
		this.myVoteTarget = null;
	}

	protected abstract void decideNextVote();

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public Agent divine() {
		return null;
	}

	/**
	 * 自分以外で生きているAgentのうち最も対象のroleらしいと思うAgentを探索
	 * 
	 * @param role
	 *            対象のrole
	 * @param rolePossibility
	 *            roleの可能性の行列
	 * @return 最もroleらしいと思うAgent
	 */
	protected Agent estimateRolerAgent(Role role, double[][] rolePossibility) {
		List<Agent> agents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		Agent me = this.myGameInfo.latestGameInfo.getAgent();
		double possibility = Double.MIN_VALUE;
		Agent result = null;
		for (Agent agent : agents) {
			// 自分は飛ばす
			if (agent.getAgentIdx() == me.getAgentIdx()) {
				continue;
			}
			if (possibility < rolePossibility[agent.getAgentIdx() - 1][role.ordinal()]) {
				possibility = rolePossibility[agent.getAgentIdx() - 1][role.ordinal()];
				result = agent;
			}
		}
		return result;
	}

	/**
	 * roleMapを推測し，発話する
	 */
	protected void estimateRoleMap() {
		List<Agent> agents = this.myGameInfo.latestGameInfo.getAliveAgentList();
		List<Role> assumedRoles = Arrays.asList(agents.stream().map(x -> this.assumeRole(x)).toArray(Role[]::new));
		for (int i = 0; i < agents.size(); ++i) {
			Role assumedRole = assumedRoles.get(i);
			if (assumedRole.equals(Role.SEER) || assumedRole.equals(Role.WEREWOLF)) {
				this.myEstimate.addLast(new Content(new EstimateContentBuilder(agents.get(i), assumedRole)));
			}
		}
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

	protected double[][] genVoteDistanceMatrix(GameSetting gameSetting) {
		// 領域作成、 初期化
		double[][] result = new double[gameSetting.getPlayerNum()][gameSetting.getPlayerNum()];
		for (int i = 0; i < gameSetting.getPlayerNum(); ++i) {
			for (int j = 0; j < gameSetting.getPlayerNum(); ++j) {
				result[i][j] = 0.0;
			}
		}
		return result;
	}

	/**
	 * 投票結果を格納するための領域作成、初期化
	 * 
	 * @param gameInfo
	 * @param gameSetting
	 * @return
	 */
	protected List<Integer>[] genVoteResults(GameSetting gameSetting) {
		// 領域作成、初期化
		@SuppressWarnings("unchecked")
		List<Integer>[] result = new List[gameSetting.getPlayerNum()];
		for (int i = 0; i < gameSetting.getPlayerNum(); ++i) {
			result[i] = new ArrayList<Integer>();
		}

		return result;
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

	protected void handleEstimate(Agent agent, Content content) {
		// estimateについては，村専用とするため，ここでは実装しない
	}

	/**
	 * targetの村人の可能性を上げ，その分狼の確率を下げる．
	 * 負の値を与えた場合，逆の効果．
	 * ただし，村および狼の確率が負にはならないようにする．
	 * また，狼および村の確率が1.0, 0.0の場合は値をいじらない: 確定情報をいじらない
	 * 
	 * @param target
	 *            対象のAgent
	 * @param value
	 *            変位量
	 * @return 値を変えたかどうか
	 */
	protected boolean addVillagerPossibility(Agent target, double value) {
		double villagerPossibility = this.rolePossibility[target.getAgentIdx() - 1][Role.VILLAGER.ordinal()];
		double werewolfPossibility = this.rolePossibility[target.getAgentIdx() - 1][Role.WEREWOLF.ordinal()];
		if (villagerPossibility < MIN_POSSIBILITY || villagerPossibility + MIN_POSSIBILITY > 1.0) {
			// 確定情報とみなし，変更しない
			return false;
		}
		if (werewolfPossibility < MIN_POSSIBILITY || werewolfPossibility + MIN_POSSIBILITY > 1.0) {
			// 確定情報とみなし，変更しない
			return false;
		}
		villagerPossibility += value;
		werewolfPossibility -= value;
		// 加算と減算で区別して処理
		if (value > 0.0) {
			// 加算の場合の整合性チェック
			if (villagerPossibility + MIN_POSSIBILITY > 1.0 || werewolfPossibility < MIN_POSSIBILITY) {
				// 修正する
				double delta = Math.max(villagerPossibility - (1.0 - MIN_POSSIBILITY),
						MIN_POSSIBILITY - werewolfPossibility);
				villagerPossibility -= delta;
				werewolfPossibility += delta;
			}
		} else {
			// 減算の場合の整合性チェック
			if (villagerPossibility < MIN_POSSIBILITY || werewolfPossibility + MIN_POSSIBILITY > 1.0) {
				// 修正する
				double delta = Math.max(MIN_POSSIBILITY - villagerPossibility,
						werewolfPossibility - (1.0 - MIN_POSSIBILITY));
				villagerPossibility += delta;
				werewolfPossibility -= delta;
			}
		}
		return true;
	}

	/**
	 * 配信されるgameInfoとgameSettingを保存
	 */
	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		this.myGameInfo = new BaseGameInfo(gameInfo, gameSetting);

		// 前の情報を引き継がないようにするためクリア
		this.saidEstimation = false;
		this.talkSkipCount = 0;

		// divineResults作成
		this.rolePossibility = genRolePossibility(gameInfo, gameSetting);
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

	public void showRolePossibility() {
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
	 * 発言処理．myDeclare->myVode->myEstimateの順にたまった発言リストを処理．なくなればskipする
	 */
	@Override
	public String talk() {
		/*
		if (this.talkSkipCount <= 1 && this.randomAction()) {
			this.talkSkipCount++;
			// Skipが許される状況においては，ランダムでskipを選択する
			Content skip = new Content(new SkipContentBuilder());
			return skip.toString();
		}*/
		this.talkSkipCount = 0;
		if (!this.myDeclare.isEmpty()) {
			Content declare = this.myDeclare.pop();
			return declare.getText();
		} else if (!this.myVote.isEmpty()) {
			Content vote = this.myVote.pop();
			return vote.getText();
		} else if (!this.myEstimate.isEmpty()) {
			Content estimate = this.myEstimate.pop();
			return estimate.getText();
		} else {
			Content skip = new Content(new SkipContentBuilder());
			return skip.toString();
		}
	}
	
	protected void updateNextVoteTarget(Agent newAgent) {
		if(newAgent == null) {
			return;
		}
		if(!newAgent.equals(this.myVoteTarget)) {
			this.myVote.clear();
			this.myVoteTarget = newAgent;
			this.myVote.addLast(new Content(new VoteContentBuilder(this.myVoteTarget)));
		}
	}

	/**
	 * 配信されるgameInfoを保存，
	 * 新しく配信されたtalkを認識し，処理する．
	 * また，更新情報に基づいてestimate情報をupdateする
	 */
	@Override
	public void update(GameInfo gameInfo) {
		this.myGameInfo.update(gameInfo);

		// updateの内容にしたがってtalkを生成する
		this.genVoteTalk();
		this.genEstimateTalk();
		// デバッグ用の出力
		// this.showRoleProbability();
	}

	/**
	 * voteTalkを生成する
	 */
	protected void genVoteTalk() {
		// 発話生成
		// 投票予定のAgentを決め，提案する
		if (this.myGameInfo.currentDay >= 1) {
			if (!this.checkNextVoteAgentsStatus()) {
				decideNextVote();
			}
		}
	}

	/**
	 * estimateTalkを生成する
	 */
	protected abstract void genEstimateTalk();

	/**
	 * 投票行動．
	 * - すでに投票行動を決めているなら，それに投票
	 * - 決まっていない場合は，生きているAgentのうち，最も狼らしいAgentに投票する．
	 */
	@Override
	public Agent vote() {
		if (this.myVoteTarget != null) {
			return this.myVoteTarget;
		}
		// 宣言が見つからなければ，即興で自分視点で最も狼らしいAgentに投票
		return this.estimateRolerAgent(Role.WEREWOLF, this.rolePossibility);
	}

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public String whisper() {
		return null;
	}
}
