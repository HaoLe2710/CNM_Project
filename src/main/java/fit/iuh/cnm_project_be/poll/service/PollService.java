package fit.iuh.cnm_project_be.poll.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.poll.dto.request.CreatePollRequest;
import fit.iuh.cnm_project_be.poll.dto.request.PollOptionRequest;
import fit.iuh.cnm_project_be.poll.dto.request.UpdatePollRequest;
import fit.iuh.cnm_project_be.poll.dto.response.PollOptionResponse;
import fit.iuh.cnm_project_be.poll.dto.response.PollResponse;
import fit.iuh.cnm_project_be.poll.entity.Poll;
import fit.iuh.cnm_project_be.poll.entity.PollOption;
import fit.iuh.cnm_project_be.poll.entity.PollVote;
import fit.iuh.cnm_project_be.poll.enums.PollStatus;
import fit.iuh.cnm_project_be.poll.repository.PollOptionRepository;
import fit.iuh.cnm_project_be.poll.repository.PollRepository;
import fit.iuh.cnm_project_be.poll.repository.PollVoteRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PollService {

    private static final int MAX_OPTIONS = 20;

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    @Transactional(readOnly = true)
    public List<PollResponse> list(UUID conversationId, UUID currentUserId) {
        ensureConversationMember(conversationId, currentUserId);
        return pollRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(conversationId)
                .stream()
                .map(poll -> toResponse(poll, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public PollResponse get(UUID pollId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureConversationMember(poll.getConversationId(), currentUserId);
        return toResponse(poll, currentUserId);
    }

    @Transactional
    public PollResponse create(UUID conversationId, UUID currentUserId, CreatePollRequest request) {
        ensureConversationMember(conversationId, currentUserId);
        List<String> options = normalizeCreateOptions(request.getOptions());

        Poll poll = new Poll();
        poll.setConversationId(conversationId);
        poll.setCreatorId(currentUserId);
        poll.setQuestion(normalizeQuestion(request.getQuestion()));
        poll.setMultipleChoice(Boolean.TRUE.equals(request.getMultipleChoice()));
        poll.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()));
        poll.setExpiresAt(request.getExpiresAt());
        Poll savedPoll = pollRepository.save(poll);

        for (int i = 0; i < options.size(); i++) {
            PollOption option = new PollOption();
            option.setPollId(savedPoll.getId());
            option.setText(options.get(i));
            option.setPosition(i);
            pollOptionRepository.save(option);
        }

        return toResponse(savedPoll, currentUserId);
    }

    @Transactional
    public PollResponse update(UUID pollId, UUID currentUserId, UpdatePollRequest request) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        ensureEditable(poll);

        String question = trimToNull(request.getQuestion());
        if (question != null) {
            poll.setQuestion(normalizeQuestion(question));
        }
        if (request.getMultipleChoice() != null) {
            poll.setMultipleChoice(request.getMultipleChoice());
            if (!request.getMultipleChoice()) {
                collapseMyVotesForSingleChoice(poll.getId());
            }
        }
        if (request.getAnonymous() != null) {
            poll.setAnonymous(request.getAnonymous());
        }
        if (request.getExpiresAt() != null) {
            poll.setExpiresAt(request.getExpiresAt());
        }

        return toResponse(pollRepository.save(poll), currentUserId);
    }

    @Transactional
    public void delete(UUID pollId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        Instant now = Instant.now();
        poll.setDeletedAt(now);
        pollRepository.save(poll);
    }

    @Transactional
    public PollResponse close(UUID pollId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        if (poll.getStatus() == PollStatus.CLOSED) {
            return toResponse(poll, currentUserId);
        }
        poll.setStatus(PollStatus.CLOSED);
        poll.setClosedAt(Instant.now());
        return toResponse(pollRepository.save(poll), currentUserId);
    }

    @Transactional
    public PollResponse addOption(UUID pollId, UUID currentUserId, PollOptionRequest request) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        ensureEditable(poll);
        if (pollOptionRepository.countByPollIdAndDeletedAtIsNull(pollId) >= MAX_OPTIONS) {
            throw new BusinessException("Poll can have at most 20 options");
        }

        PollOption option = new PollOption();
        option.setPollId(pollId);
        option.setText(normalizeQuestion(request.getText()));
        option.setPosition((int) pollOptionRepository.countByPollIdAndDeletedAtIsNull(pollId));
        pollOptionRepository.save(option);
        return toResponse(poll, currentUserId);
    }

    @Transactional
    public PollResponse updateOption(UUID pollId, UUID optionId, UUID currentUserId, PollOptionRequest request) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        ensureEditable(poll);
        PollOption option = getOptionOrThrow(pollId, optionId);
        option.setText(normalizeQuestion(request.getText()));
        pollOptionRepository.save(option);
        return toResponse(poll, currentUserId);
    }

    @Transactional
    public PollResponse deleteOption(UUID pollId, UUID optionId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureOwner(poll, currentUserId);
        ensureEditable(poll);
        if (pollOptionRepository.countByPollIdAndDeletedAtIsNull(pollId) <= 2) {
            throw new BusinessException("Poll must keep at least 2 options");
        }
        PollOption option = getOptionOrThrow(pollId, optionId);
        option.setDeletedAt(Instant.now());
        pollOptionRepository.save(option);
        pollVoteRepository.deleteByOptionIdIn(List.of(optionId));
        return toResponse(poll, currentUserId);
    }

    @Transactional
    public PollResponse vote(UUID pollId, UUID optionId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureConversationMember(poll.getConversationId(), currentUserId);
        ensureActiveForVoting(poll);
        getOptionOrThrow(pollId, optionId);

        if (!poll.isMultipleChoice()) {
            pollVoteRepository.deleteByPollIdAndUserId(pollId, currentUserId);
        }
        if (pollVoteRepository.findByPollIdAndOptionIdAndUserId(pollId, optionId, currentUserId).isEmpty()) {
            PollVote vote = new PollVote();
            vote.setPollId(pollId);
            vote.setOptionId(optionId);
            vote.setUserId(currentUserId);
            pollVoteRepository.save(vote);
        }
        return toResponse(poll, currentUserId);
    }

    @Transactional
    public PollResponse unvote(UUID pollId, UUID optionId, UUID currentUserId) {
        Poll poll = getPollOrThrow(pollId);
        ensureConversationMember(poll.getConversationId(), currentUserId);
        getOptionOrThrow(pollId, optionId);
        pollVoteRepository.deleteByPollIdAndOptionIdAndUserId(pollId, optionId, currentUserId);
        return toResponse(poll, currentUserId);
    }

    private PollResponse toResponse(Poll poll, UUID currentUserId) {
        List<PollOption> options = pollOptionRepository.findByPollIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(poll.getId());
        List<PollVote> votes = pollVoteRepository.findByPollId(poll.getId());
        Map<UUID, List<PollVote>> votesByOption = votes.stream()
                .collect(Collectors.groupingBy(PollVote::getOptionId, LinkedHashMap::new, Collectors.toList()));
        List<UUID> myOptionIds = votes.stream()
                .filter(vote -> vote.getUserId().equals(currentUserId))
                .map(PollVote::getOptionId)
                .toList();

        return PollResponse.builder()
                .id(poll.getId())
                .conversationId(poll.getConversationId())
                .creatorId(poll.getCreatorId())
                .question(poll.getQuestion())
                .multipleChoice(poll.isMultipleChoice())
                .anonymous(poll.isAnonymous())
                .status(resolveStatus(poll))
                .expiresAt(poll.getExpiresAt())
                .closedAt(poll.getClosedAt())
                .totalVotes(votes.size())
                .myOptionIds(myOptionIds)
                .options(options.stream()
                        .map(option -> toOptionResponse(option, votesByOption.getOrDefault(option.getId(), List.of()), currentUserId, poll.isAnonymous()))
                        .toList())
                .createdAt(poll.getCreatedAt())
                .updatedAt(poll.getUpdatedAt())
                .build();
    }

    private PollOptionResponse toOptionResponse(PollOption option, List<PollVote> votes, UUID currentUserId, boolean anonymous) {
        return PollOptionResponse.builder()
                .id(option.getId())
                .text(option.getText())
                .position(option.getPosition())
                .voteCount(votes.size())
                .votedByMe(votes.stream().anyMatch(vote -> vote.getUserId().equals(currentUserId)))
                .voterIds(anonymous ? List.of() : votes.stream()
                        .map(PollVote::getUserId)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList())
                .build();
    }

    private Poll getPollOrThrow(UUID pollId) {
        return pollRepository.findByIdAndDeletedAtIsNull(pollId)
                .orElseThrow(() -> new NotFoundException("Poll not found"));
    }

    private PollOption getOptionOrThrow(UUID pollId, UUID optionId) {
        return pollOptionRepository.findByIdAndPollIdAndDeletedAtIsNull(optionId, pollId)
                .orElseThrow(() -> new NotFoundException("Poll option not found"));
    }

    private void ensureConversationMember(UUID conversationId, UUID userId) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("User does not belong to this conversation");
        }
    }

    private void ensureOwner(Poll poll, UUID currentUserId) {
        ensureConversationMember(poll.getConversationId(), currentUserId);
        if (!poll.getCreatorId().equals(currentUserId)) {
            throw new ForbiddenException("Only poll creator can modify this poll");
        }
    }

    private void ensureEditable(Poll poll) {
        if (resolveStatus(poll) == PollStatus.CLOSED) {
            throw new BusinessException("Poll is closed");
        }
        if (!pollVoteRepository.findByPollId(poll.getId()).isEmpty()) {
            throw new BusinessException("Poll with votes cannot be edited");
        }
    }

    private void ensureActiveForVoting(Poll poll) {
        if (resolveStatus(poll) == PollStatus.CLOSED) {
            throw new BusinessException("Poll is closed");
        }
    }

    private PollStatus resolveStatus(Poll poll) {
        if (poll.getStatus() == PollStatus.CLOSED) {
            return PollStatus.CLOSED;
        }
        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(Instant.now())) {
            return PollStatus.CLOSED;
        }
        return PollStatus.ACTIVE;
    }

    private List<String> normalizeCreateOptions(List<String> options) {
        if (options == null || options.size() < 2) {
            throw new BusinessException("Poll requires at least 2 options");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new BusinessException("Poll can have at most 20 options");
        }
        List<String> normalized = options.stream()
                .map(this::normalizeQuestion)
                .distinct()
                .toList();
        if (normalized.size() < 2) {
            throw new BusinessException("Poll requires at least 2 distinct options");
        }
        return normalized;
    }

    private void collapseMyVotesForSingleChoice(UUID pollId) {
        Map<UUID, List<PollVote>> votesByUser = pollVoteRepository.findByPollId(pollId)
                .stream()
                .collect(Collectors.groupingBy(PollVote::getUserId));
        votesByUser.values().forEach(votes -> votes.stream()
                .sorted(Comparator.comparing(PollVote::getCreatedAt))
                .skip(1)
                .forEach(pollVoteRepository::delete));
    }

    private String normalizeQuestion(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("Poll text is required");
        }
        if (normalized.length() > 255) {
            throw new BusinessException("Poll text must be at most 255 characters");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
