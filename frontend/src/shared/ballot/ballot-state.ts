import type { Ballot, VoteResponse, VoteType } from '@/shared/api/types';

export function applyVoteResponse(ballot: Ballot, response: VoteResponse): Ballot {
  return { ...ballot, ...response, id: ballot.id };
}

export function applyOptimisticVote(ballot: Ballot, type: VoteType): Ballot {
  let upVotes = Math.max(0, ballot.upVotes);
  let downVotes = Math.max(0, ballot.downVotes);
  const removing = ballot.myVote === type;

  if (ballot.myVote === 'UP') upVotes = Math.max(0, upVotes - 1);
  if (ballot.myVote === 'DOWN') downVotes = Math.max(0, downVotes - 1);
  if (!removing && type === 'UP') upVotes += 1;
  if (!removing && type === 'DOWN') downVotes += 1;

  return {
    ...ballot,
    upVotes,
    downVotes,
    totalVotes: upVotes + downVotes,
    voteScore: upVotes - downVotes,
    myVote: removing ? undefined : type
  };
}

export function mergeUniqueBallots(current: Ballot[], incoming: Ballot[]): Ballot[] {
  const known = new Set(current.map(ballot => ballot.id));
  return [...current, ...incoming.filter(ballot => !known.has(ballot.id))];
}
