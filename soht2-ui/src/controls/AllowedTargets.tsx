/* SOHT2 © Licensed under MIT 2025. */
import MultiInputField from './MultiInputField';

const RE_TARGET = /^[a-z0-9.*-]+:[0-9*]+$/;

export default function AllowedTargets({
  targets,
  onChange,
}: Readonly<{ targets: string[]; onChange: (targets: string[]) => void }>) {
  return (
    <MultiInputField<string>
      label="Allowed Target"
      placeholder="e.g. host:123 or *.host:*"
      values={targets ?? []}
      valueInputPredicate={v => RE_TARGET.test(v)}
      valueErrorHint="Expected something like host:123 or *.host:*."
      onChange={onChange}
    />
  );
}
