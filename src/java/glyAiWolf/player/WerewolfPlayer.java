package glyAiWolf.player;

import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.SkipContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.net.GameInfo;

public class WerewolfPlayer extends BasePlayer {
	// 処理した他狼の囁きリスト
	protected Deque<Talk> processedWhispers = new ConcurrentLinkedDeque<>();
	// 囁き予定リスト．囁けば順に消えていく
	private Deque<Content> myWhispers = new ConcurrentLinkedDeque<>();

	@Override
	public void dayStart() {
		// TODO 自動生成されたメソッド・スタブ
		super.dayStart();
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
