package com.ssafy.lyricit.game.dto;

import java.util.List;

import com.ssafy.lyricit.room.dto.RoomDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameDto {
	private Long playerCount;
	private Long roundTime;
	private Long roundLimit;
	private Long currentRound;
	private String keyword;
	private Long answerCount;
	private List<ScoreDto> members;

}