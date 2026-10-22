import {describe, expect, it} from '@jest/globals';
import {
  expandTeredoCandidates,
  filterUnreachableRemoteCandidates,
  preferNetworkIceCandidates,
  preferPublicIceCandidates,
} from '../src/xCloud/teredoCandidates';

describe('expandTeredoCandidates', () => {
  it('adds the console IPv4 from Teredo and normalizes the original candidate', () => {
    const sourceCandidate = {
      candidate:
        'a=candidate:1 1 UDP 16777215 2001:0000:4136:e378:8000:63bf:3fff:fdd2 50000 typ srflx',
      messageType: 'iceCandidate',
      sdpMLineIndex: '0',
      sdpMid: '0',
    };

    expect(expandTeredoCandidates([sourceCandidate])).toEqual([
      {
        ...sourceCandidate,
        candidate:
          'candidate:1 1 udp 16777215 2001:0000:4136:e378:8000:63bf:3fff:fdd2 50000 typ srflx',
      },
      {
        candidate:
          'candidate:2 1 udp 1694498815 192.0.2.45 40000 typ srflx raddr 0.0.0.0 rport 0',
        messageType: 'iceCandidate',
        sdpMLineIndex: '0',
        sdpMid: '0',
      },
      {
        candidate:
          'candidate:3 1 udp 1694498815 192.0.2.45 50000 typ srflx raddr 0.0.0.0 rport 0',
        messageType: 'iceCandidate',
        sdpMLineIndex: '0',
        sdpMid: '0',
      },
      {
        candidate:
          'candidate:4 1 udp 1694498815 192.0.2.45 3074 typ srflx raddr 0.0.0.0 rport 0',
        messageType: 'iceCandidate',
        sdpMLineIndex: '0',
        sdpMid: '0',
      },
    ]);
  });

  it('does not add duplicate port 3074 candidates', () => {
    const sourceCandidate = {
      candidate:
        'candidate:1 1 UDP 16777215 2001:0000:4136:e378:8000:63bf:3fff:fdd2 3074 typ srflx',
    };

    const candidates = expandTeredoCandidates([sourceCandidate]);

    expect(candidates.map(({candidate}) => candidate)).toEqual([
      'candidate:1 1 udp 16777215 2001:0000:4136:e378:8000:63bf:3fff:fdd2 3074 typ srflx',
      'candidate:2 1 udp 1694498815 192.0.2.45 40000 typ srflx raddr 0.0.0.0 rport 0',
      'candidate:3 1 udp 1694498815 192.0.2.45 3074 typ srflx raddr 0.0.0.0 rport 0',
    ]);
  });

  it('normalizes non-Teredo candidates without adding addresses', () => {
    const candidates = [
      {
        candidate: 'a=candidate:1 1 UDP 16777215 2001:db8::1 50000 typ host',
      },
    ];

    expect(expandTeredoCandidates(candidates)).toEqual([
      {
        candidate: 'candidate:1 1 udp 16777215 2001:db8::1 50000 typ host',
      },
    ]);
  });

  it('prioritizes IPv6 candidates without changing relative order or end marker', () => {
    const candidates = [
      {
        candidate: 'a=candidate:1 1 UDP 100 192.0.2.1 5000 typ host',
      },
      {
        candidate: 'a=candidate:2 1 UDP 100 2001:db8::1 5000 typ host',
      },
      {
        candidate: 'a=candidate:3 1 UDP 100 2001:db8::2 5001 typ host',
      },
      {
        candidate: 'a=candidate:4 1 UDP 100 192.0.2.2 5001 typ host',
      },
      {candidate: 'a=end-of-candidates'},
    ];

    expect(expandTeredoCandidates(candidates, true)).toEqual([
      {
        candidate: 'candidate:2 1 udp 100 2001:db8::1 5000 typ host',
      },
      {
        candidate: 'candidate:3 1 udp 100 2001:db8::2 5001 typ host',
      },
      {
        candidate: 'candidate:1 1 udp 100 192.0.2.1 5000 typ host',
      },
      {
        candidate: 'candidate:4 1 udp 100 192.0.2.2 5001 typ host',
      },
      {candidate: 'a=end-of-candidates'},
    ]);
  });

  it('keeps a real public srflx candidate instead of inventing ports', () => {
    const candidates = [
      {
        candidate:
          'candidate:1 1 udp 2122255103 2001:0000:4136:e378:8000:63bf:3fff:fdd2 50000 typ host',
      },
      {
        candidate:
          'candidate:2 1 udp 1685987071 192.0.2.45 29172 typ srflx raddr 192.168.1.105 rport 9002',
      },
    ];

    expect(expandTeredoCandidates(candidates).map(({candidate}) => candidate)).toEqual(
      candidates.map(({candidate}) => candidate),
    );
  });

  it('drops the console LAN address when the phone is on another network', () => {
    const remote = [
      {candidate: 'candidate:1 1 udp 2122194687 192.168.1.105 9002 typ host'},
      {
        candidate:
          'candidate:2 1 udp 1685987071 177.125.131.93 29172 typ srflx raddr 192.168.1.105 rport 9002',
      },
    ];
    const local = [
      {candidate: 'candidate:3 1 udp 2122063615 10.0.0.1 34371 typ host'},
    ];

    expect(filterUnreachableRemoteCandidates(remote, local)).toEqual([remote[1]]);
  });

  it('lowers private host priority when a public candidate exists', () => {
    const candidates = preferPublicIceCandidates([
      {
        candidate: 'candidate:1 1 udp 2122063615 10.0.0.1 34371 typ host',
        sdpMid: '0',
        sdpMLineIndex: 0,
      },
      {
        candidate:
          'candidate:2 1 tcp 1518280447 172.17.140.174 9 typ host tcptype active',
        sdpMid: '0',
        sdpMLineIndex: 0,
      },
      {
        candidate:
          'candidate:3 1 udp 1685855999 185.222.213.36 52873 typ srflx raddr 10.0.0.1 rport 34371',
        sdpMid: '0',
        sdpMLineIndex: 0,
      },
    ]);

    expect(candidates.map(({candidate}) => candidate)).toEqual([
      'candidate:1 1 udp 100 10.0.0.1 34371 typ host',
      'candidate:3 1 udp 1685855999 185.222.213.36 52873 typ srflx raddr 10.0.0.1 rport 34371',
    ]);
  });

  it('keeps the LAN address ahead of the public address on the same network', () => {
    const candidates = preferNetworkIceCandidates(
      [
        {
          candidate: 'candidate:1 1 udp 2122063615 192.168.1.20 34371 typ host',
          sdpMid: '0',
          sdpMLineIndex: 0,
        },
        {
          candidate:
            'candidate:2 1 udp 1685855999 185.222.213.36 52873 typ srflx raddr 192.168.1.20 rport 34371',
          sdpMid: '0',
          sdpMLineIndex: 0,
        },
      ],
      ['192.168.1.20'],
      '255.255.255.0',
    );

    expect(candidates.map(({candidate}) => candidate)).toEqual([
      'candidate:1 1 udp 2122063615 192.168.1.20 34371 typ host',
      'candidate:2 1 udp 100 185.222.213.36 52873 typ srflx raddr 192.168.1.20 rport 34371',
    ]);
  });

  it('keeps a console on the same subnet mask even across /24 boundaries', () => {
    const remote = [
      {candidate: 'candidate:1 1 udp 2122194687 192.168.1.105 9002 typ host'},
      {
        candidate:
          'candidate:2 1 udp 1685987071 177.125.131.93 29172 typ srflx raddr 192.168.1.105 rport 9002',
      },
    ];
    const local = [
      {candidate: 'candidate:3 1 udp 2122063615 192.168.0.20 34371 typ host'},
    ];

    expect(
      filterUnreachableRemoteCandidates(remote, local, {
        ips: ['192.168.0.20'],
        mask: '255.255.254.0',
      }),
    ).toEqual(remote);
  });
});
