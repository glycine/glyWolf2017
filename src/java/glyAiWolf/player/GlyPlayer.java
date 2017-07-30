package glyAiWolf.player;

import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

/**
 * サーバから実行されるためのクラス． initializeでroleに応じてplayerのインスタンスを作り，あとの処理はplayerに丸投げ
 * 
 * @author glycine
 *
 */
public class GlyPlayer implements Player {
	private BasePlayer player = null;

	@Override
	public Agent attack() {
		return player.attack();
	}

	@Override
	public void dayStart() {
		player.dayStart();
	}

	@Override
	public Agent divine() {
		return player.divine();
	}

	@Override
	public void finish() {
		player.finish();

	}

	@Override
	public String getName() {
		return player.getName();
	}

	@Override
	public Agent guard() {
		return player.guard();
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		switch (gameInfo.getRole()) {
		case BODYGUARD:
			this.player = new BodyguardPlayer();
			break;
		case FOX:
			this.player = new BasePlayer();
			break;
		case FREEMASON:
			this.player = new BasePlayer();
			break;
		case MEDIUM:
			this.player = new MediumPlayer();
			break;
		case POSSESSED:
			this.player = new PossessedPlayer();
			break;
		case SEER:
			this.player = new SeerPlayer();
			break;
		case VILLAGER:
		default:
			this.player = new VillagerPlayer();
			break;
		case WEREWOLF:
			this.player = new BasePlayer();
			break;
		}
		player.initialize(gameInfo, gameSetting);
	}

	@Override
	public String talk() {
		return player.talk();
	}

	@Override
	public void update(GameInfo gameInfo) {
		player.update(gameInfo);
	}

	@Override
	public Agent vote() {
		return player.vote();
	}

	@Override
	public String whisper() {
		return player.whisper();
	}

}
