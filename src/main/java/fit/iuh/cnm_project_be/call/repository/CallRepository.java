package fit.iuh.cnm_project_be.call.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.call.entity.Call;

import java.util.List;
import java.util.UUID;

public interface CallRepository
        extends BaseRepository<Call, UUID> {

    List<Call> findByCallerId(UUID callerId);
}