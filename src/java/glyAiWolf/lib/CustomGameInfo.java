package glyAiWolf.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.data.Vote;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * カスタマイズしたGameInfo
 * 今は投票以外はあまり日にちの違いは考えないことにする
 * 
 * @author "Haruhisa Ishida<haruhisa.ishida@gmail.com>"
 *
 */
public class CustomGameInfo {
	// ゲーム日数の最大値
	private static final int MAX_DAY = 8;
	public int currentDay;
	// 襲撃されたAgents
	public Set<Agent> attackedAgents;
	// 追放されたAgents
	public Set<Agent> executedAgents;
	// 各プレイヤーのCO結果, [agentIndex]で引く
	public Role[] coResults;
	// 各プレイヤーに対して占いリクエストが誰からだれに出ているか．
	// [agentIndex][taretIndex]で引き，あるときはtrue,
	// そうでないときはfalse
	public boolean[][] diviationRequests;
	// 各プレイヤーの占い結果，[agentIndex][targetIndex]で引く
	public Species[][] divineResults;
	// 各プレイヤーのEstimate結果, [agentIndex][targetIndex]で引く
	public Role[][] estimateStates;
	// 護衛対象に関して，リクエストが誰からだれに出ているか．
	// [agentIndex][targetIndex]で引く，
	// あるときはtrue, ないときはfalse
	public boolean[][] guardRequests;
	// 各プレイヤーの護衛結果，[agentIndex][dayNum]で引く
	public Agent[][] guardResults;
	// 各プレイヤーの霊媒結果，[agentIndex][taretIndex]で引く
	public Species[][] identResults;
	// 各プレイヤーのVote宣言，[agentIndex][dayNum]で引く
	public Agent[][] voteStates;
	// 各プレイヤーのVoteの実際の結果．[agentIndex][dayNum]で引く
	public Agent[][] voteResults;
	// 各プレイヤーのtalkの総カウントとtopicごとのカウント
	// [agentIndex][dayNum][topicIndex]で引く
	public int totalTalkCount[][];
	public int talkCount[][][];
	// 最新のgameInfo
	public GameInfo latestGameInfo;
	// このゲームのsetting
	public GameSetting gameSetting;
	// このゲームで観測されたtalk
	public Deque<Talk> talks;
	// このゲームで観測されたvote
	public Deque<Vote> votes;

	public CustomGameInfo(GameInfo gameInfo, GameSetting gameSetting) {
		this.gameSetting = gameSetting;
		this.latestGameInfo = gameInfo;
		this.currentDay = gameInfo.getDay();
		initialize(gameSetting.getPlayerNum());
	}

	public Map<Agent, Integer> getVoteStatusCount(int day) {
		Map<Agent, Integer> result = new HashMap<>();
		for (Agent agent : this.latestGameInfo.getAgentList()) {
			Agent voteTarget = this.voteStates[agent.getAgentIdx() - 1][day];
			if (voteTarget != null) {
				if (!result.containsKey(voteTarget)) {
					result.put(voteTarget, 1);
				} else {
					result.put(voteTarget, result.get(voteTarget) + 1);
				}
			}
		}

		return result;
	}

	/**
	 * 日の代わりに呼び出す.
	 * request系の変数初期化
	 */
	public void dayChange() {
		int playerNum = this.gameSetting.getPlayerNum();
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.diviationRequests[i], false);
			Arrays.fill(this.guardRequests[i], false);
		}
	}

	/**
	 * COしている占い全てから黒出しされているAgent
	 * targetは, 占われている, 一部から占われている, 占われていない
	 * 
	 * @return
	 */
	public Set<Agent> getBlack() {
		Set<Agent> seers = this.getCOAgents(Role.SEER);
		Set<Agent> result = new HashSet<>();
		for (Agent target : this.latestGameInfo.getAgentList()) {
			List<Species> divineResults = new ArrayList<>();
			for (Agent seer : seers) {
				Species divineResult = this.divineResults[seer.getAgentIdx() - 1][target.getAgentIdx() - 1];
				if (divineResult != null) {
					divineResults.add(divineResult);
				}
			}
			if (divineResults.size() >= 2) {
				if (!divineResults.contains(Species.HUMAN)) {
					// 黒出しのみ
					result.add(target);
				}
			}
		}
		return result;
	}

	/**
	 * roleの役職でCOしているagentSetを返す．
	 * 
	 * @param role
	 * @return COしているagentのSet
	 */
	public Set<Agent> getCOAgents(Role role) {
		Set<Agent> result = new HashSet<>();
		for (Agent agent : this.latestGameInfo.getAgentList()) {
			Role coRole = this.coResults[agent.getAgentIdx() - 1];
			if (role.equals(coRole)) {
				result.add(agent);
			}
		}
		return result;
	}

	/**
	 * 占い結果が1つのみ，あるいは白黒の両方がある人
	 * 
	 * @return
	 */
	public Set<Agent> getGray() {
		Set<Agent> seers = this.getCOAgents(Role.SEER);
		Set<Agent> result = new HashSet<>();
		for (Agent target : this.latestGameInfo.getAgentList()) {
			List<Species> divineResults = new ArrayList<>();
			for (Agent seer : seers) {
				Species divineResult = this.divineResults[seer.getAgentIdx() - 1][target.getAgentIdx() - 1];
				if (divineResult != null) {
					divineResults.add(divineResult);
				}
			}
			if (divineResults.size() == 1) {
				if (!divineResults.contains(Species.WEREWOLF)) {
					// 黒出し1回
					result.add(target);
				}
			} else if (divineResults.size() >= 2) {
				if (divineResults.contains(Species.HUMAN) && divineResults.contains(Species.WEREWOLF)) {
					// 白黒両方ある
					result.add(target);
				}
			}
		}
		return result;
	}

	/**
	 * 占い結果が複数あり黒出しが存在しないAgentを返す
	 * 
	 * @return
	 */
	public Set<Agent> getWhite() {
		Set<Agent> seers = this.getCOAgents(Role.SEER);
		Set<Agent> result = new HashSet<>();
		for (Agent target : this.latestGameInfo.getAgentList()) {
			List<Species> divineResults = new ArrayList<>();
			for (Agent seer : seers) {
				Species divineResult = this.divineResults[seer.getAgentIdx() - 1][target.getAgentIdx() - 1];
				if (divineResult != null) {
					divineResults.add(divineResult);
				}
			}
			if (divineResults.size() >= 2) {
				if (divineResults.contains(Species.WEREWOLF)) {
					// 白出しのみ
					result.add(target);
				}
			}
		}
		return result;
	}

	private void handleTalk(Talk talk) {
		Agent agent = talk.getAgent();
		Content content = new Content(talk.getText());

		switch (content.getTopic()) {
		case AGREE:
			// talkに関して同意: 未実装
			break;
		case ATTACK:
			// 襲撃対象: 未実装
			break;
		case COMINGOUT:
			this.coResults[agent.getAgentIdx() - 1] = content.getRole();
			break;
		case DISAGREE:
			// talkに関して反対: 未実装
			break;
		case DIVINATION:
			// 占い要求
			this.diviationRequests[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = true;
			break;
		case DIVINED:
			// 占い結果
			this.divineResults[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = content.getResult();
			break;
		case ESTIMATE:
			this.estimateStates[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = content.getRole();
			break;
		case GUARD:
			// 護衛要求
			this.guardRequests[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = true;
			break;
		case GUARDED:
			// 護衛報告
			this.guardResults[agent.getAgentIdx() - 1][this.currentDay] = content.getTarget();
			break;
		case IDENTIFIED:
			// 霊媒報告
			this.identResults[agent.getAgentIdx() - 1][content.getTarget().getAgentIdx() - 1] = content.getResult();
			break;
		case OPERATOR:
			break;
		case OVER:
			break;
		case SKIP:
			break;
		case VOTE:
			this.voteStates[agent.getAgentIdx() - 1][this.currentDay] = content.getTarget();
			break;
		default:
			break;
		}
	}

	private void initialize(int playerNum) {
		this.currentDay = 0;
		// attackedAgents
		this.attackedAgents = new HashSet<>();
		// executedAgents
		this.executedAgents = new HashSet<>();
		// coResults
		this.coResults = new Role[playerNum];
		Arrays.fill(this.coResults, null);
		// diviatoinRequests
		this.diviationRequests = new boolean[playerNum][playerNum];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.diviationRequests[i], false);
		}
		// divineResults
		this.divineResults = new Species[playerNum][playerNum];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.divineResults[i], null);
		}
		// estimateResults
		this.estimateStates = new Role[playerNum][playerNum];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.estimateStates[i], null);
		}
		// guardRequests
		this.guardRequests = new boolean[playerNum][playerNum];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.guardRequests[i], false);
		}
		// guardResults
		this.guardResults = new Agent[playerNum][MAX_DAY];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.guardResults[i], null);
		}
		// identResults
		this.identResults = new Species[playerNum][MAX_DAY];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.identResults[i], null);
		}
		// voteFacts
		this.voteResults = new Agent[playerNum][MAX_DAY];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.voteResults[i], null);
		}
		// voteResults
		this.voteStates = new Agent[playerNum][MAX_DAY];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.voteStates[i], null);
		}
		// totalTalkCount
		this.totalTalkCount = new int[playerNum][MAX_DAY];
		for (int i = 0; i < playerNum; ++i) {
			Arrays.fill(this.totalTalkCount[i], 0);
		}
		this.talkCount = new int[playerNum][MAX_DAY][Topic.values().length];
		for (int i = 0; i < playerNum; ++i) {
			for (int j = 0; j < MAX_DAY; ++j) {
				Arrays.fill(this.talkCount[i][j], 0);
			}
		}

		// talks
		this.talks = new ConcurrentLinkedDeque<>();
		// votes
		this.votes = new ConcurrentLinkedDeque<>();
	}

	/**
	 * 生きているAgentのうち，投票宣言と投票結果が異なるAgent
	 * 
	 * @return
	 */
	public Set<Agent> getVoteIllegulars() {
		Set<Agent> result = new HashSet<>();
		for (Agent agent : this.latestGameInfo.getAliveAgentList()) {
			for (int i = 0; i <= this.currentDay; ++i) {
				Agent voteStatus = this.voteStates[agent.getAgentIdx() - 1][i];
				Agent voteResult = this.voteResults[agent.getAgentIdx() - 1][i];
				if (voteStatus != null && voteResult != null) {
					if (!voteResult.equals(voteStatus)) {
						// 投票先について，宣言と結果が異なる．
						result.add(agent);
					}
				}
			}
		}

		return result;
	}

	/**
	 * 配信された情報に基づいて保存データを更新
	 * 
	 * @param gameInfo
	 */
	public void update(GameInfo gameInfo) {
		// 日付情報更新
		this.currentDay = gameInfo.getDay();
		// 最新のgameInfoに更新
		this.latestGameInfo = gameInfo;
		// 襲撃，追放を更新
		if (this.latestGameInfo.getAttackedAgent() != null) {
			this.attackedAgents.add(this.latestGameInfo.getAttackedAgent());
		}
		if (this.latestGameInfo.getExecutedAgent() != null) {
			this.executedAgents.add(this.latestGameInfo.getExecutedAgent());
		}

		// talk内容を反映する
		// まず，新しくきたtalkを抽出する
		Deque<Talk> newTalks = new ConcurrentLinkedDeque<>();
		for (Talk talk : gameInfo.getTalkList()) {
			if (this.talks.isEmpty() || !talk.equals(talks.getLast())) {
				newTalks.addLast(talk);
			}
		}
		while (!newTalks.isEmpty()) {
			Talk talk = newTalks.pollFirst();
			Content content = new Content(talk.getText());
			this.handleTalk(talk);
			this.talks.addLast(talk);
			this.totalTalkCount[talk.getAgent().getAgentIdx() - 1][talk.getDay()]++;
			this.talkCount[talk.getAgent().getAgentIdx() - 1][talk.getDay()][content.getTopic().ordinal()]++;
		}
		// vote情報を反映する
		Deque<Vote> newVotes = new ConcurrentLinkedDeque<>();
		for (Vote vote : gameInfo.getLatestVoteList()) {
			if (this.votes.isEmpty() || !vote.equals(this.votes.getLast())) {
				newVotes.addLast(vote);
			}
		}
		while (!newVotes.isEmpty()) {
			Vote vote = newVotes.pollFirst();
			this.voteResults[vote.getAgent().getAgentIdx() - 1][vote.getDay()] = vote.getTarget();
			this.votes.addLast(vote);
		}
	}
}
