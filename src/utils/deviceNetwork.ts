import NetInfo from '@react-native-community/netinfo';
import {
  collectPrivateHostIps,
  isPrivateIpv4,
} from '../xCloud/teredoCandidates';

export type DeviceLan = {
  ips: string[];
  mask?: string;
};

export async function readDeviceLan(
  candidates: Array<{candidate?: string}> = [],
): Promise<DeviceLan> {
  let reportedIp = '';
  let mask: string | undefined;
  try {
    const state = await NetInfo.fetch();
    const details: any = state?.details ?? {};
    if (typeof details.ipAddress === 'string') {
      reportedIp = details.ipAddress;
    }
    if (typeof details.subnet === 'string' && details.subnet.includes('.')) {
      mask = details.subnet;
    }
  } catch {
    reportedIp = '';
  }

  const ips = Array.from(
    new Set(
      [reportedIp, ...collectPrivateHostIps(candidates)].filter(ip =>
        isPrivateIpv4(ip),
      ),
    ),
  );
  return {ips, mask};
}
