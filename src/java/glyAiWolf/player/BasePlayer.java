package glyAiWolf.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.ContentBuilder;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class BasePlayer implements Player {
	protected List<Role> villagerSide = new ArrayList<>();
	protected List<Role> werewolfSide = new ArrayList<>();
	// 日ごとのgameInfo
	protected Map<Integer, GameInfo> gameInfos = new TreeMap<>();
	// 最新のgameInfo
	protected GameInfo latestGameInfo;
	// 今回のゲームのgameSetting, initialize時にコピーし，後はそのまま
	protected GameSetting gameSetting;
	protected BlockingDeque<Talk> processedTalks = new LinkedBlockingDeque<>();
	// 各プレイヤーの各役職の可能性
	protected double[][] rolePossibility;
	// 各プレイヤーの各プレイヤーに対する行動行列
	protected int[][][] talkMatrix;

	/**
	 * BasePlayerでは実装しない
	 */
	@Override
	public Agent attack() {
		return null;
	}

	@Override
	public void dayStart() {
		// TODO 自動生成されたメソッド・スタブ
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
			double diff = Math.abs(targetProb - opposeProb);

			System.err.println("seerProb: " + seerProb + ", targetProb: " + targetProb + ", opposeProb: " + opposeProb
					+ ", diff: " + diff);
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

	/**
	 * 配信されるgameInfoとgameSettingを保存
	 */
	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		// 前の情報を引き継がないようにするためクリア
		this.gameInfos.clear();
		this.processedTalks.clear();

		// 値のコピー
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
		this.latestGameInfo = gameInfo;
		this.gameSetting = gameSetting.clone();
		// 各役職の可能性の行列を作成する
		this.rolePossibility = genRolePossibility(gameInfo, gameSetting);
		this.talkMatrix = genTalkMatrix(gameInfo, gameSetting);

		// デバッグ用の出力
		this.showRoleProbability();
	}

	protected void showRoleProbability() {
		for (double[] row : rolePossibility) {
			for (double elem : row) {
				System.err.print(elem + ", ");
			}
			System.err.println("");
		}
	}

	/**
	 * 発言処理．今のところは無発言
	 */
	@Override
	public String talk() {
		int myIndex = this.latestGameInfo.getAgent().getAgentIdx() - 1;

		// 自分自身の役職をCOしていなければ，coする
		if (this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()] == 0) {
			this.talkMatrix[myIndex][myIndex][Topic.COMINGOUT.ordinal()]++;
			Agent me = this.latestGameInfo.getAgent();
			Role coRole = this.latestGameInfo.getRole();
			ContentBuilder contentBuilder = new ComingoutContentBuilder(me, coRole);
			return contentBuilder.toString();
		}

		ContentBuilder contentBuilder = new SkipContentBuilder();
		return contentBuilder.toString();
	}

	/**
	 * 配信されるgameInfoを保存
	 */
	@Override
	public void update(GameInfo gameInfo) {
		this.gameInfos.put(gameInfo.getDay(), gameInfo);
		this.latestGameInfo = gameInfo;
		BlockingDeque<Talk> newTalks = new LinkedBlockingDeque<>();
		for (Talk talk : gameInfo.getTalkList()) {
			if (processedTalks.isEmpty() || !talk.equals(processedTalks.getLast())) {
				newTalks.addFirst(talk);
			}
		}
		System.err.println("newTalksSize: " + newTalks.size());
		while (!newTalks.isEmpty()) {
			Talk talk = newTalks.pollFirst();
			handleTalk(talk);
			this.processedTalks.addFirst(talk);
		}

		this.showRoleProbability();
	}

	/**
	 * 投票行動．生きているAgentのうち，最も狼らしいAgentに投票する．
	 */
	@Override
	public Agent vote() {
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
}
