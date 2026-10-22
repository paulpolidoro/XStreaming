import {Address6} from 'ip-address';

type IceCandidateData = {
  candidate: string;
  messageType?: string;
  sdpMLineIndex?: string;
  sdpMid?: string;
  [key: string]: any;
};

const CANDIDATE_IP_PATTERN = /^[0-9a-f:.]+$/i;
const DEFAULT_CANDIDATE_PORT = 9002;
const XBOX_GAME_PORT = 3074;
const SRFLX_PRIORITY = 1694498815;
const END_OF_CANDIDATES = 'a=end-of-candidates';

export function normalizeIceCandidateLine(candidate: string): string {
  const trimmed = candidate.trim();
  if (
    trimmed === END_OF_CANDIDATES ||
    trimmed === 'end-of-candidates' ||
    trimmed === 'a=end-of-candidates'
  ) {
    return END_OF_CANDIDATES;
  }

  const withoutPrefix = trimmed.startsWith('a=')
    ? trimmed.slice(2).trim()
    : trimmed;
  const parts = withoutPrefix.split(/\s+/);
  if (parts.length >= 3 && /^(udp|tcp)$/i.test(parts[2])) {
    parts[2] = parts[2].toLowerCase();
  }
  return parts.join(' ');
}

function isEndOfCandidates(candidate: string): boolean {
  return normalizeIceCandidateLine(candidate) === END_OF_CANDIDATES;
}

export function expandTeredoCandidates(
  exchangeCandidates: IceCandidateData[],
  prioritizeIpv6 = false,
): IceCandidateData[] {
  const candidates = exchangeCandidates.map(candidate => ({
    ...candidate,
    candidate: normalizeIceCandidateLine(candidate.candidate),
  }));
  let insertedCandidates = 0;

  const knownSrflxIps = new Set(
    candidates
      .filter(candidate => /\styp srflx(?:\s|$)/.test(candidate.candidate))
      .map(candidate => candidate.candidate.trim().split(/\s+/)[4])
      .filter(Boolean),
  );

  exchangeCandidates.forEach((sourceCandidate, sourceIndex) => {
    const tokens = sourceCandidate.candidate.trim().split(/\s+/);
    const generated: IceCandidateData[] = [];
    const seen = new Set<string>();
    let foundation = sourceIndex + 2;

    const addConsoleCandidate = (ip: string, hostPort: number) => {
      if (!ip || !Number.isFinite(hostPort) || hostPort <= 0) {
        return;
      }
      const key = `${ip}:${hostPort}`;
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
      generated.push({
        candidate: `candidate:${foundation} 1 udp ${SRFLX_PRIORITY} ${ip} ${hostPort} typ srflx raddr 0.0.0.0 rport 0`,
        messageType: sourceCandidate.messageType,
        sdpMLineIndex: sourceCandidate.sdpMLineIndex,
        sdpMid: sourceCandidate.sdpMid,
      });
      foundation += 1;
    };

    for (let tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++) {
      const address = tokens[tokenIndex];
      if (!CANDIDATE_IP_PATTERN.test(address) || !Address6.isValid(address)) {
        continue;
      }

      const teredo = new Address6(address).inspectTeredo();
      if (teredo.prefix !== '2001:0000') {
        continue;
      }
      if (knownSrflxIps.has(teredo.client4)) {
        continue;
      }
      const port = Number.parseInt(tokens[tokenIndex + 1], 10);
      const candidatePort = Number.isNaN(port) ? DEFAULT_CANDIDATE_PORT : port;
      addConsoleCandidate(teredo.client4, Number(teredo.udpPort));
      addConsoleCandidate(teredo.client4, candidatePort);
      addConsoleCandidate(teredo.client4, XBOX_GAME_PORT);
    }

    if (generated.length > 0) {
      candidates.splice(sourceIndex + insertedCandidates + 1, 0, ...generated);
      insertedCandidates += generated.length;
    }
  });

  if (!prioritizeIpv6) {
    return candidates;
  }

  const endOfCandidates = candidates.filter(({candidate}) =>
    isEndOfCandidates(candidate),
  );
  const orderedCandidates = candidates.filter(
    ({candidate}) => !isEndOfCandidates(candidate),
  );

  return orderedCandidates
    .map((candidate, index) => ({candidate, index}))
    .sort((first, second) => {
      const firstIp = first.candidate.candidate.trim().split(/\s+/)[4];
      const secondIp = second.candidate.candidate.trim().split(/\s+/)[4];
      const firstIsIpv6 = firstIp?.includes(':') ?? false;
      const secondIsIpv6 = secondIp?.includes(':') ?? false;

      if (firstIsIpv6 === secondIsIpv6) {
        return first.index - second.index;
      }
      return firstIsIpv6 ? -1 : 1;
    })
    .map(({candidate}) => candidate)
    .concat(endOfCandidates);
}

export function appendServerCandidates(
  candidates: IceCandidateData[],
  serverDetails?: {
    ipAddress?: string;
    port?: number;
    ipV4Address?: string;
    ipV4Port?: number;
    ipV6Address?: string | null;
    ipV6Port?: number;
  } | null,
): IceCandidateData[] {
  if (!serverDetails) {
    return candidates;
  }

  const extras: IceCandidateData[] = [];
  const add = (ip?: string | null, port?: number) => {
    if (!ip || !port || port <= 0) {
      return;
    }
    const alreadyPresent = candidates.some(candidate =>
      candidate.candidate.includes(`${ip} ${port}`),
    );
    if (alreadyPresent) {
      return;
    }
    extras.push({
      candidate: `candidate:${extras.length + 20} 1 udp ${SRFLX_PRIORITY} ${ip} ${port} typ srflx raddr 0.0.0.0 rport 0`,
      messageType: 'iceCandidate',
      sdpMid: '0',
      sdpMLineIndex: '0',
    });
  };

  add(
    serverDetails.ipV4Address || serverDetails.ipAddress,
    serverDetails.ipV4Port || serverDetails.port,
  );
  add(serverDetails.ipV6Address, serverDetails.ipV6Port);
  return extras.length > 0 ? candidates.concat(extras) : candidates;
}

type IceCandidateLike = {
  candidate?: string;
  sdpMid?: string;
  sdpMLineIndex?: number | string;
  [key: string]: any;
};

function candidateParts(candidate: string) {
  const parts = normalizeIceCandidateLine(candidate).split(/\s+/);
  return {
    protocol: (parts[2] || '').toLowerCase(),
    priority: parts[3] || '',
    ip: parts[4] || '',
    type: parts[7] || '',
    parts,
  };
}

export function isPrivateIpv4(ip: string): boolean {
  const match = ip.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
  if (!match) {
    return false;
  }
  const first = Number(match[1]);
  const second = Number(match[2]);
  if (first === 10 || first === 127 || first === 192 && second === 168) {
    return true;
  }
  if (first === 172 && second >= 16 && second <= 31) {
    return true;
  }
  if (first === 169 && second === 254) {
    return true;
  }
  if (first === 100 && second >= 64 && second <= 127) {
    return true;
  }
  return false;
}

function ipv4ToInt(ip: string): number | null {
  const match = ip.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
  if (!match) {
    return null;
  }
  const parts = [match[1], match[2], match[3], match[4]].map(Number);
  if (parts.some(part => part > 255)) {
    return null;
  }
  return (((parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]) >>>
    0);
}

function sameIpv4Network(
  first: string,
  second: string,
  subnetMask?: string,
): boolean {
  const left = ipv4ToInt(first);
  const right = ipv4ToInt(second);
  const mask = ipv4ToInt(subnetMask || '255.255.255.0');
  if (left === null || right === null || mask === null) {
    return false;
  }
  return (left & mask) === (right & mask);
}

const LAN_PRIORITY = '2122260223';
const FALLBACK_PRIORITY = '100';

function withPriority(line: string, priority: string): string {
  const parts = line.split(/\s+/);
  parts[3] = priority;
  return parts.join(' ');
}

function keepLanPriority(priority: string): string {
  const value = Number(priority);
  if (Number.isFinite(value) && value >= 1000000) {
    return priority;
  }
  return LAN_PRIORITY;
}

export function collectPrivateHostIps(candidates: IceCandidateLike[]): string[] {
  const ips = candidates
    .map(candidate => candidateParts(candidate.candidate || ''))
    .filter(
      candidate =>
        candidate.protocol === 'udp' &&
        candidate.type === 'host' &&
        isPrivateIpv4(candidate.ip),
    )
    .map(candidate => candidate.ip);
  return Array.from(new Set(ips));
}

export function preferNetworkIceCandidates(
  candidates: IceCandidateLike[],
  deviceIps: string[] = [],
  subnetMask?: string,
) {
  const localPrivate = deviceIps.filter(isPrivateIpv4);
  const parsed = candidates.map(candidate =>
    candidateParts(candidate.candidate || ''),
  );
  const onDeviceNetwork = (ip: string) =>
    localPrivate.some(local => sameIpv4Network(local, ip, subnetMask));
  const hasLanCandidate = parsed.some(
    candidate =>
      candidate.protocol === 'udp' &&
      onDeviceNetwork(candidate.ip) &&
      (candidate.type === 'host' || isPrivateIpv4(candidate.ip)),
  );

  if (!hasLanCandidate) {
    return preferPublicIceCandidates(candidates);
  }

  return candidates
    .map((candidate, index) => ({candidate, parsed: parsed[index]}))
    .filter(({parsed: candidate}) => candidate.protocol === 'udp')
    .map(({candidate, parsed: info}) => {
      const line = normalizeIceCandidateLine(candidate.candidate || '');
      const priority = onDeviceNetwork(info.ip)
        ? keepLanPriority(info.priority)
        : FALLBACK_PRIORITY;
      return {
        candidate: withPriority(line, priority),
        sdpMid: candidate.sdpMid ?? '0',
        sdpMLineIndex: candidate.sdpMLineIndex ?? 0,
      };
    });
}

export function preferPublicIceCandidates(candidates: IceCandidateLike[]) {
  const parsed = candidates.map(candidate =>
    candidateParts(candidate.candidate || ''),
  );
  const hasPublicUdp = parsed.some(
    candidate =>
      candidate.protocol === 'udp' &&
      (candidate.type === 'srflx' || candidate.type === 'relay'),
  );

  return candidates
    .map((candidate, index) => ({candidate, parsed: parsed[index]}))
    .filter(({parsed: candidate}) => candidate.protocol === 'udp')
    .map(({candidate, parsed: info}) => {
      let line = normalizeIceCandidateLine(candidate.candidate || '');
      if (
        hasPublicUdp &&
        info.type === 'host' &&
        isPrivateIpv4(info.ip)
      ) {
        const parts = line.split(/\s+/);
        parts[3] = '100';
        line = parts.join(' ');
      }
      return {
        candidate: line,
        sdpMid: candidate.sdpMid ?? '0',
        sdpMLineIndex: candidate.sdpMLineIndex ?? 0,
      };
    });
}

export function filterUnreachableRemoteCandidates(
  remoteCandidates: IceCandidateLike[],
  localCandidates: IceCandidateLike[],
  network?: {ips?: string[]; mask?: string},
) {
  const localIps = Array.from(
    new Set(
      localCandidates
        .map(candidate => candidateParts(candidate.candidate || '').ip)
        .concat(network?.ips || [])
        .filter(Boolean),
    ),
  );

  return remoteCandidates.filter(candidate => {
    const line = candidate.candidate || '';
    if (normalizeIceCandidateLine(line) === END_OF_CANDIDATES) {
      return false;
    }
    const info = candidateParts(line);
    if (info.protocol && info.protocol !== 'udp') {
      return false;
    }
    if (info.type === 'host' && isPrivateIpv4(info.ip)) {
      return localIps.some(ip =>
        sameIpv4Network(ip, info.ip, network?.mask),
      );
    }
    return true;
  });
}
