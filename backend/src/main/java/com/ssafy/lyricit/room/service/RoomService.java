package com.ssafy.lyricit.room.service;

import static com.ssafy.lyricit.exception.ErrorCode.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.lyricit.common.GlobalEventResponse;
import com.ssafy.lyricit.common.type.EventType;
import com.ssafy.lyricit.exception.BaseException;
import com.ssafy.lyricit.member.domain.Member;
import com.ssafy.lyricit.member.repository.MemberRepository;
import com.ssafy.lyricit.room.domain.Room;
import com.ssafy.lyricit.room.dto.RoomDto;
import com.ssafy.lyricit.room.dto.RoomOutsideDto;
import com.ssafy.lyricit.room.dto.RoomRequestDto;
import com.ssafy.lyricit.room.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomService {
	private final RedisTemplate<String, Object> roomRedisTemplate;
	private final RoomRepository roomRepository;
	private final MemberRepository memberRepository;
	private final SimpMessagingTemplate template; // 특정 Broker로 메세지를 전달
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	public String createRoom(String memberId, RoomRequestDto roomRequest) {
		// to mysql
		Member member = memberRepository.findById(memberId).orElseThrow(() -> new BaseException(MEMBER_NOT_FOUND));
		Room room = roomRequest.toEntity(member);
		roomRepository.save(room);

		// to redis
		RoomDto roomDto = room.toDto(member.toInGameDto());
		String roomNumber = findEmptyRoomNumber();
		roomRedisTemplate.opsForValue().set(roomNumber, roomDto);

		log.info("\n [방 생성 완료] \n== mysql 저장 ==\n {} \n", room);
		log.info("\n== redis 저장 == \n [{}번 방] \n {}", roomNumber, roomDto);

		RoomOutsideDto roomOutsideDto = roomDto.toOutsideDto(roomNumber);

		// pub to lounge
		template.convertAndSend("/sub/lounge",
			GlobalEventResponse.builder()
				.type(EventType.CREATED.name())
				.data(roomOutsideDto));
		return roomNumber;
	}

	public List<RoomOutsideDto> readAllRooms() {// 라운지 접근 시 단 한번 호출
		Set<String> roomNumbers = roomRedisTemplate.keys("*"); // get all keys
		List<RoomOutsideDto> roomOutsideDtoList = new ArrayList<>();

		if (roomNumbers != null) {
			for (String roomNumber : roomNumbers) {
				RoomDto roomDto = (RoomDto)roomRedisTemplate.opsForValue().get(roomNumber); // get value by key
				if (roomDto != null) {
					RoomOutsideDto roomOutsideDto = roomDto.toOutsideDto(roomNumber);
					roomOutsideDtoList.add(roomOutsideDto);
				}
			}
		}

		log.info("\n [모든 방 조회 결과] \n {}", roomOutsideDtoList);
		return roomOutsideDtoList;
	}

	public RoomOutsideDto readRoomByRoomNumber(String roomNumber) {
		if (Boolean.FALSE.equals(roomRedisTemplate.hasKey(roomNumber))) {
			throw new BaseException(ROOM_NOT_FOUND);
		}

		RoomOutsideDto roomOutsideDto =
			((RoomDto)roomRedisTemplate.opsForValue().get(roomNumber)).toOutsideDto(roomNumber);

		log.info("\n [방 조회 결과] \n {}", roomOutsideDto);
		return roomOutsideDto;
	}

	// 방 삭제 메서드
	public void deleteRoomByRoomNumber(String roomNumber) {
		if (Boolean.FALSE.equals(roomRedisTemplate.hasKey(roomNumber))) {
			throw new BaseException(ROOM_NOT_FOUND);
		}
		RoomDto roomDto = (RoomDto)roomRedisTemplate.opsForValue().get(roomNumber); // todo: add null exception

		// delete from mysql
		Room room = roomRepository.findById(roomDto.getRoomId()).orElseThrow(() -> new BaseException(ROOM_NOT_FOUND));
		room.setDeleted(true); // soft deletion

		// delete from redis
		roomRedisTemplate.delete(roomNumber);

		log.info("\n [방 삭제 완료] : {}", roomNumber);

		// pub to lounge -> client do delete job
		template.convertAndSend("/sub/lounge",
			GlobalEventResponse.builder()
				.type(EventType.DELETED.name())
				.data(roomDto.toOutsideDto(roomNumber)));
	}

	private String findEmptyRoomNumber() {
		long roomNumber = 1L;
		String roomNumberStr;
		do {
			roomNumberStr = Long.toString(roomNumber);
			roomNumber++;
		} while (Boolean.TRUE.equals(roomRedisTemplate.hasKey(roomNumberStr)));
		return roomNumberStr;
	}
}
