import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://flashsale:8081';
const activityId = __ENV.ACTIVITY_ID || '1';
const userBase = Number(__ENV.USER_BASE || '1000000');
const accepted = new Counter('business_accepted');
const businessRejected = new Counter('business_rejected');
const validResponse = new Rate('business_valid_response');
const knownCodes = new Set([
    'ACCEPTED', 'NOT_STARTED', 'ENDED', 'SOLD_OUT', 'DUPLICATE',
    'NOT_READY', 'QUEUE_UNAVAILABLE', 'PUBLISH_PENDING',
]);

export const options = {
    vus: Number(__ENV.K6_VUS || '20'),
    duration: __ENV.K6_DURATION || '10s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
        business_valid_response: ['rate>0.99'],
    },
};

export default function () {
    const sequence = (__VU * 1000000) + __ITER;
    const userId = userBase + sequence;
    const requestId = `load-${__VU}-${__ITER}-${Date.now()}`;
    const response = http.post(
        `${baseUrl}/api/activities/${activityId}/reservations`,
        null,
        {
            headers: {
                'X-User-Id': String(userId),
                'X-Request-Id': requestId,
            },
            tags: { endpoint: 'reservation' },
        },
    );

    let code = 'INVALID_RESPONSE';
    try {
        code = response.json('data.code') || code;
    } catch (_) {
        // check 会把非 JSON 或缺少业务码的响应计为失败。
    }

    const valid = response.status === 200 && knownCodes.has(code);
    validResponse.add(valid);
    if (code === 'ACCEPTED' || code === 'PUBLISH_PENDING') {
        accepted.add(1);
    } else if (knownCodes.has(code)) {
        businessRejected.add(1);
    }
    check(response, {
        'HTTP 200 with known business code': () => valid,
    });
}