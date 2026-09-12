// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title VoiceRegistry
 * @notice Tamper-resistant on-chain registry for VoiceShield biometric voice identity commitments.
 * @dev Stores ONLY cryptographic commitments (Keccak-256 hashes) and version metadata.
 * NO raw biometric data, raw audio, phone numbers, or personal identifying information is stored on-chain.
 */
contract VoiceRegistry {
    struct VoiceIdentity {
        bytes32 voiceHash;       // Keccak-256 hash of canonical voice embedding
        uint256 version;         // Identity version (increments on update)
        uint256 registeredAt;    // Unix timestamp of initial registration
        uint256 updatedAt;       // Unix timestamp of last update
        bool active;             // Whether this voice identity is active or revoked
    }

    address public owner;

    // Authorized backend service wallets that can register, update, or revoke identities
    mapping(address => bool) public authorizedServices;

    // Mapping from non-sensitive user reference identifier (keccak256(userId)) to VoiceIdentity
    mapping(bytes32 => VoiceIdentity) private identities;

    event VoiceRegistered(
        bytes32 indexed userRefId,
        bytes32 indexed voiceHash,
        uint256 version,
        uint256 timestamp
    );

    event VoiceUpdated(
        bytes32 indexed userRefId,
        bytes32 indexed newVoiceHash,
        uint256 newVersion,
        uint256 timestamp
    );

    event VoiceRevoked(
        bytes32 indexed userRefId,
        uint256 timestamp
    );

    event ServiceAuthorized(address indexed service, bool authorized);
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);

    modifier onlyOwner() {
        require(msg.sender == owner, "VoiceRegistry: caller is not the owner");
        _;
    }

    modifier onlyAuthorized() {
        require(msg.sender == owner || authorizedServices[msg.sender], "VoiceRegistry: caller is not authorized");
        _;
    }

    constructor() {
        owner = msg.sender;
        authorizedServices[msg.sender] = true;
    }

    function transferOwnership(address newOwner) external onlyOwner {
        require(newOwner != address(0), "VoiceRegistry: new owner is the zero address");
        emit OwnershipTransferred(owner, newOwner);
        owner = newOwner;
    }

    function setAuthorizedService(address service, bool authorized) external onlyOwner {
        require(service != address(0), "VoiceRegistry: zero address invalid");
        authorizedServices[service] = authorized;
        emit ServiceAuthorized(service, authorized);
    }

    /**
     * @notice Register a new voice identity commitment for a user reference identifier.
     * @param userRefId keccak256 hash of non-sensitive user identifier
     * @param voiceHash keccak256 commitment of the canonical voice embedding
     */
    function registerVoice(bytes32 userRefId, bytes32 voiceHash) external onlyAuthorized {
        require(userRefId != bytes32(0), "VoiceRegistry: invalid userRefId");
        require(voiceHash != bytes32(0), "VoiceRegistry: invalid voiceHash");

        VoiceIdentity storage id = identities[userRefId];
        require(id.registeredAt == 0 || !id.active, "VoiceRegistry: identity already active, use updateVoice");

        uint256 newVersion = id.version + 1;
        identities[userRefId] = VoiceIdentity({
            voiceHash: voiceHash,
            version: newVersion,
            registeredAt: block.timestamp,
            updatedAt: block.timestamp,
            active: true
        });

        emit VoiceRegistered(userRefId, voiceHash, newVersion, block.timestamp);
    }

    /**
     * @notice Update an existing voice identity commitment (e.g. after controlled re-enrollment).
     */
    function updateVoice(bytes32 userRefId, bytes32 newVoiceHash) external onlyAuthorized {
        require(userRefId != bytes32(0), "VoiceRegistry: invalid userRefId");
        require(newVoiceHash != bytes32(0), "VoiceRegistry: invalid voiceHash");

        VoiceIdentity storage id = identities[userRefId];
        require(id.registeredAt > 0 && id.active, "VoiceRegistry: identity not active");

        id.voiceHash = newVoiceHash;
        id.version += 1;
        id.updatedAt = block.timestamp;

        emit VoiceUpdated(userRefId, newVoiceHash, id.version, block.timestamp);
    }

    /**
     * @notice Revoke an active voice identity commitment.
     */
    function revokeVoice(bytes32 userRefId) external onlyAuthorized {
        require(userRefId != bytes32(0), "VoiceRegistry: invalid userRefId");

        VoiceIdentity storage id = identities[userRefId];
        require(id.active, "VoiceRegistry: identity not active");

        id.active = false;
        id.updatedAt = block.timestamp;

        emit VoiceRevoked(userRefId, block.timestamp);
    }

    /**
     * @notice View an existing voice identity commitment. Public read operation.
     */
    function getVoiceIdentity(bytes32 userRefId) external view returns (
        bytes32 voiceHash,
        uint256 version,
        uint256 registeredAt,
        uint256 updatedAt,
        bool active
    ) {
        VoiceIdentity memory id = identities[userRefId];
        return (
            id.voiceHash,
            id.version,
            id.registeredAt,
            id.updatedAt,
            id.active
        );
    }

    /**
     * @notice Verify whether a candidate voice hash matches the active on-chain commitment.
     */
    function verifyVoiceHash(bytes32 userRefId, bytes32 candidateHash) external view returns (bool isValid) {
        VoiceIdentity memory id = identities[userRefId];
        return (id.active && id.voiceHash == candidateHash);
    }
}
